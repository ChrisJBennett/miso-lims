package uk.ac.bbsrc.tgac.miso.persistence.impl;

import static uk.ac.bbsrc.tgac.miso.core.util.BoxUtils.extractBoxableInformation;
import static uk.ac.bbsrc.tgac.miso.core.util.LimsUtils.isStringEmptyOrNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.type.LongType;
import org.hibernate.type.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.eaglegenomics.simlims.core.Note;
import com.eaglegenomics.simlims.core.SecurityProfile;
import com.eaglegenomics.simlims.core.store.SecurityStore;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

import uk.ac.bbsrc.tgac.miso.core.data.Boxable;
import uk.ac.bbsrc.tgac.miso.core.data.DetailedSample;
import uk.ac.bbsrc.tgac.miso.core.data.Identity;
import uk.ac.bbsrc.tgac.miso.core.data.Project;
import uk.ac.bbsrc.tgac.miso.core.data.Sample;
import uk.ac.bbsrc.tgac.miso.core.data.impl.IdentityImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.SampleImpl;
import uk.ac.bbsrc.tgac.miso.core.service.naming.SiblingNumberGenerator;
import uk.ac.bbsrc.tgac.miso.core.store.ChangeLogStore;
import uk.ac.bbsrc.tgac.miso.core.store.LibraryStore;
import uk.ac.bbsrc.tgac.miso.core.store.NoteStore;
import uk.ac.bbsrc.tgac.miso.core.store.SampleQcStore;
import uk.ac.bbsrc.tgac.miso.core.store.Store;
import uk.ac.bbsrc.tgac.miso.core.util.LimsUtils;
import uk.ac.bbsrc.tgac.miso.persistence.SampleDao;
import uk.ac.bbsrc.tgac.miso.sqlstore.util.DbUtils;

/**
 * This is the Hibernate DAO for Samples and serves as the bridge between Hibernate and the existing SqlStore persistence layers.
 *
 * The data from the Sample table is loaded via Hibernate, but Hibernate cannot follow the references to Libraries and such from a Sample.
 * Therefore, this implementation loads a Sample via Hibernate, then calls into the SqlStore persistence layer to gather the remaining data
 * that Hibernate cannot access. Similarly, it then follows any necessary links on save. All the SqlStore-populated fields are marked
 * “transient” in the Sample class.
 */
@Transactional(rollbackFor = Exception.class)
public class HibernateSampleDao implements SampleDao, SiblingNumberGenerator {

  protected static final Logger log = LoggerFactory.getLogger(HibernateSampleDao.class);

  private boolean autoGenerateIdentificationBarcodes;

  private ChangeLogStore changeLogDao;

  private LibraryStore libraryDao;

  private NoteStore noteDao;

  private SampleQcStore sampleQcDao;

  private SecurityStore securityDao;

  private Store<SecurityProfile> securityProfileDao;

  @Autowired
  private SessionFactory sessionFactory;

  @Autowired
  private JdbcTemplate template;

  @Autowired
  private CacheManager cacheManager;

  @Override
  public Long addSample(final Sample sample) throws IOException {
    if (sample.getSecurityProfile() != null) {
      sample.setSecurityProfileId(getSecurityProfileDao().save(sample.getSecurityProfile()));
    }
    return (Long) currentSession().save(sample);
  }

  @Override
  public int getNextSiblingNumber(String partialAlias) throws IOException {
    // Find highest existing siblingNumber matching this partialAlias
    Query query = currentSession().createQuery("select max(siblingNumber) from DetailedSampleImpl as ds"
            + " where alias IN (concat(:alias, ds.siblingNumber), concat(:alias, '0', ds.siblingNumber))");
    query.setString("alias", partialAlias);
    Number result = ((Number) query.uniqueResult());
    int next = result == null ? 0 : result.intValue();

    // Increment and verify uniqueness. If alias is used, fix siblingNumber for existing sample. Repeat until unique
    Query verifyQuery = null;
    do {
      next++;
      verifyQuery = currentSession().createQuery("update DetailedSampleImpl ds set ds.siblingNumber = :siblingNumber"
          + " where alias IN (concat(:alias, :siblingNumber), concat(:alias, '0', :siblingNumber))");
      verifyQuery.setString("alias", partialAlias).setString("siblingNumber", String.valueOf(next));
    } while (verifyQuery.executeUpdate() > 0);

    return next;
  }

  @Override
  public int count() throws IOException {
    return getSample().size();
  }

  private Session currentSession() {
    return getSessionFactory().getCurrentSession();
  }

  @Override
  public void deleteSample(Sample sample) {
    currentSession().delete(sample);

  }

  /**
   * Fix up a Sample loaded by Hibernate by gathering the SqlStore-persisted information and mutating the object.
   *
   * @returns the original object after mutation.
   */
  private <T extends Sample> T fetchSqlStore(T sample) throws IOException {
    if (sample == null) return null;
    // Now we have to reconstitute all the things that aren't covered by Hibernate.
    sample.setSecurityProfile(securityDao.getSecurityProfileById(sample.getSecurityProfileId()));

    sample.getLibraries().clear();
    sample.getLibraries().addAll(libraryDao.listBySampleId(sample.getId()));

    sample.getSampleQCs().clear();
    sample.getSampleQCs().addAll(sampleQcDao.listBySampleId(sample.getId()));

    sample.getNotes().clear();
    sample.getNotes().addAll(noteDao.listBySample(sample.getId()));

    sample.getChangeLog().clear();
    sample.getChangeLog().addAll(changeLogDao.listAllById("Sample", sample.getId()));

    if (LimsUtils.isDetailedSample(sample)) {
      ((DetailedSample) sample).setChildren(listByParentId(sample.getId()));
    }

    extractBoxableInformation(template, sample);

    return sample;
  }

  /**
   * Fixup a collection of Samples loaded by Hibernate. This mutates the collection's contents.
   *
   * @return the original collection, having had it's contents mutated
   */
  private <T extends Iterable<U>, U extends Sample> T fetchSqlStore(T iterable) throws IOException {
    for (Sample s : iterable) {
      fetchSqlStore(s);
    }
    return iterable;
  }

  @Override
  public Sample get(long id) throws IOException {
    return getSample(id);
  }

  public boolean getAutoGenerateIdentificationBarcodes() {
    return autoGenerateIdentificationBarcodes;
  }

  @Override
  public Sample getByBarcode(String barcode) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl where identificationBarcode = :barcode");
    query.setString("barcode", barcode);
    return fetchSqlStore((Sample) query.uniqueResult());
  }

  @Override
  public Collection<Sample> getByBarcodeList(List<String> barcodeList) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl where identificationBarcode in (:barcodes)");
    query.setParameterList("barcodes", barcodeList, StringType.INSTANCE);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  @Override
  public Collection<Sample> getByIdList(List<Long> idList) throws IOException {
    if (idList.isEmpty()) {
      return Collections.emptyList();
    }
    Query query = currentSession().createQuery("from SampleImpl where sampleId in (:ids)");
    query.setParameterList("ids", idList, LongType.INSTANCE);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  @Override
  public Boxable getByPositionId(long positionId) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl where boxPositionId = :posn");
    query.setLong("posn", positionId);
    return fetchSqlStore((Sample) query.uniqueResult());
  }

  public ChangeLogStore getChangeLogDao() {
    return changeLogDao;
  }

  public JdbcTemplate getJdbcTemplate() {
    return template;
  }

  public LibraryStore getLibraryDao() {
    return libraryDao;
  }

  public NoteStore getNoteDao() {
    return noteDao;
  }

  @Override
  public List<Sample> getSample() throws IOException {
    Query query = currentSession().createQuery("from SampleImpl");
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  @Override
  public Sample getSample(Long id) throws IOException {
    return fetchSqlStore((Sample) currentSession().get(SampleImpl.class, id));
  }

  @Override
  public Long countAll() throws IOException {
    Query query = currentSession().createQuery("select count(*) from SampleImpl");
    return (Long) query.uniqueResult();
  }

  public SampleQcStore getSampleQcDao() {
    return sampleQcDao;
  }

  public SecurityStore getSecurityDao() {
    return securityDao;
  }

  public Store<SecurityProfile> getSecurityProfileDao() {
    return securityProfileDao;
  }

  /**
   * Pull a Sample without following all of the links. At the present time, this means just loading the object from Hibernate.
   */
  @Override
  public Sample lazyGet(long id) throws IOException {
    return (Sample) currentSession().get(SampleImpl.class, id);
  }

  @Override
  public Collection<Sample> listAll() throws IOException {
    return getSample();
  }

  @Override
  public Collection<Sample> listAllByReceivedDate(long limit) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl order by receivedDate desc");
    query.setMaxResults((int) limit);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  @Override
  public Collection<String> listAllSampleTypes() throws IOException {
    return getJdbcTemplate().queryForList("SELECT name FROM SampleType", String.class);
  }

  @Override
  public Collection<Sample> listAllWithLimit(long limit) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl");
    query.setMaxResults((int) limit);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  @Override
  public boolean aliasExists(String alias) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl where alias = :alias");
    query.setString("alias", alias);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return records.size() > 0;
  }

  @Override
  public Collection<Sample> listByAlias(String alias) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl where alias = :alias");
    query.setString("alias", alias);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  @Override
  public Collection<Sample> listByExperimentId(long experimentId) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl where experiment.id like :id");
    query.setLong("id", experimentId);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  /**
   * Lazy-gets samples associated with a given Project
   *
   * @param Long
   *          projectId
   * @return Collection<Sample> samples
   */
  @Override
  public Collection<Sample> listByProjectId(long projectId) throws IOException {
    @SuppressWarnings("unchecked")
    List<Sample> records = currentSession().createCriteria(SampleImpl.class).add(Restrictions.eq("project.id", projectId)).list();
    return records;
  }

  private static final String[] searchProperties = new String[] { "alias", "identificationBarcode", "name" };

  /**
   * Create a Hibernate criterion to search for all the properties our users want to search.
   *
   * @param querystr
   * @return
   */
  private Criterion searchRestrictions(String querystr) {
    String str = DbUtils.convertStringToSearchQuery(querystr);

    Criterion[] criteria = new Criterion[searchProperties.length + 1];
    for (int i = 0; i < searchProperties.length; i++) {
      criteria[i] = Restrictions.ilike(searchProperties[i], str, MatchMode.ANYWHERE);
    }

    criteria[searchProperties.length] = Restrictions.and(Restrictions.eq("class", IdentityImpl.class),
        Restrictions.ilike("externalName", str, MatchMode.ANYWHERE));
    return Restrictions.or(criteria);
  }

  @Override
  public Collection<Sample> listBySearch(String querystr) throws IOException {
    Criteria criteria = currentSession().createCriteria(SampleImpl.class);
    criteria.add(searchRestrictions(querystr));
    @SuppressWarnings("unchecked")
    List<Sample> records = criteria.list();
    return fetchSqlStore(records);
  }

  @Override
  public Long countBySearch(String querystr) throws IOException {
    if (isStringEmptyOrNull(querystr)) {
      return countAll();
    } else {
      Criteria criteria = currentSession().createCriteria(SampleImpl.class);
      criteria.add(searchRestrictions(querystr));
      return (Long) criteria.setProjection(Projections.rowCount()).uniqueResult();
    }
  }

  @Override
  public List<Sample> listBySearchOffsetAndNumResults(int offset, int resultsPerPage, String querystr, String sortCol, String sortDir)
      throws IOException {
    if ("lastModified".equals(sortCol)) sortCol = "derivedInfo.lastModified";
    Criteria criteria = currentSession().createCriteria(SampleImpl.class);
    criteria.add(searchRestrictions(querystr));
    // I don't know why this alias is required, but without it, you can't sort by 'derivedInfo.lastModifier', which is the field on which we
    // want to sort most List X pages
    criteria.createAlias("derivedInfo", "derivedInfo");
    criteria.setFirstResult(offset);
    criteria.setMaxResults(resultsPerPage);
    criteria.addOrder("asc".equals(sortDir) ? Order.asc(sortCol) : Order.desc(sortCol));
    criteria.setProjection(Projections.property("id"));
    @SuppressWarnings("unchecked")
    List<Long> ids = criteria.list();
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }
    // We do this in two steps to make a smaller query that that the database can optimise
    Criteria query = currentSession().createCriteria(SampleImpl.class);
    query.add(Restrictions.in("id", ids));
    query.addOrder("asc".equals(sortDir) ? Order.asc(sortCol) : Order.desc(sortCol));
    query.createAlias("derivedInfo", "derivedInfo");
    @SuppressWarnings("unchecked")
    List<Sample> requestedPage = fetchSqlStore(query.list());
    return requestedPage;
  }

  @Override
  public List<Sample> listByOffsetAndNumResults(int offset, int resultsPerPage, String sortCol, String sortDir) throws IOException {
    Criteria criteria = currentSession().createCriteria(SampleImpl.class);
    if ("lastModified".equals(sortCol)) sortCol = "derivedInfo.lastModified";
    // I don't know why this alias is required, but without it, you can't sort by 'derivedInfo.lastModifier', which is the field on which we
    // want to sort most List X pages
    criteria.createAlias("derivedInfo", "derivedInfo");
    criteria.setFirstResult(offset);
    criteria.setMaxResults(resultsPerPage);
    criteria.addOrder("asc".equals(sortDir.toLowerCase()) ? Order.asc(sortCol) : Order.desc(sortCol));
    @SuppressWarnings("unchecked")
    List<Sample> requestedPage = criteria.list();
    return fetchSqlStore(requestedPage);
  }

  @Override
  public Collection<Sample> listBySubmissionId(long submissionId) throws IOException {
    Query query = currentSession().createQuery("from SampleImpl where submissionId like :id");
    query.setLong("id", submissionId);
    @SuppressWarnings("unchecked")
    List<Sample> records = query.list();
    return fetchSqlStore(records);
  }

  private Set<DetailedSample> listByParentId(long parentId) {
    Query query = currentSession().createQuery("select s from SampleImpl s " + "join s.parent p " + "where p.sampleId = :id");
    query.setLong("id", parentId);
    @SuppressWarnings("unchecked")
    List<DetailedSample> samples = query.list();
    return new HashSet<>(samples);
  }

  /**
   * Write all the non-Hibernate data from a Sample that aren't persisted manually in the controllers.
   */
  private void persistSqlStore(Sample sample) throws IOException {
    Cache cache = cacheManager == null ? null : cacheManager.getCache(LimsUtils.noddyCamelCaseify(Project.class.getSimpleName()) + "Cache");
    if (cache != null) cache.remove(DbUtils.hashCodeCacheKeyFor(sample.getProject().getId()));

    // Now we have to persist all the things that aren't covered by Hibernate. Turns out, just notes.

    for (Note n : sample.getNotes()) {
      noteDao.saveSampleNote(sample, n);
    }
  }

  @Override
  public boolean remove(Sample t) throws IOException {
    deleteSample(t);
    return true;
  }

  @Override
  public long save(Sample t) throws IOException {
    if (t.getId() == SampleImpl.UNSAVED_ID) {
      return addSample(t);
    } else {
      update(t);
      return t.getId();
    }
  }

  public void setAutoGenerateIdentificationBarcodes(boolean autoGenerateIdentificationBarcodes) {
    this.autoGenerateIdentificationBarcodes = autoGenerateIdentificationBarcodes;
  }

  @Override
  public void setCascadeType(CascadeType cascadeType) {
  }

  public void setChangeLogDao(ChangeLogStore changeLogDao) {
    this.changeLogDao = changeLogDao;
  }

  public void setJdbcTemplate(JdbcTemplate template) {
    this.template = template;
  }

  public void setLibraryDao(LibraryStore libraryDao) {
    this.libraryDao = libraryDao;
  }

  public void setNoteDao(NoteStore noteDao) {
    this.noteDao = noteDao;
  }

  public void setSampleQcDao(SampleQcStore sampleQcDao) {
    this.sampleQcDao = sampleQcDao;
  }

  public void setSecurityDao(SecurityStore securityDao) {
    this.securityDao = securityDao;
  }

  public void setSecurityProfileDao(Store<SecurityProfile> securityProfileDao) {
    this.securityProfileDao = securityProfileDao;
  }

  @Override
  public void update(Sample sample) throws IOException {
    if (sample.getSecurityProfile() != null) {
      sample.setSecurityProfileId(sample.getSecurityProfile().getProfileId());
    }
    currentSession().update(sample);
    persistSqlStore(sample);
  }

  public SessionFactory getSessionFactory() {
    return sessionFactory;
  }

  public void setSessionFactory(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Map<String, Integer> getSampleColumnSizes() throws IOException {
    return DbUtils.getColumnSizes(template, "Sample");
  }

  @Override
  public Collection<Identity> getIdentitiesByExternalNameOrAlias(String externalName) throws IOException {
    if (isStringEmptyOrNull(externalName)) return Collections.emptySet();
    String str = DbUtils.convertStringToSearchQuery(externalName);
    Criteria criteria = currentSession().createCriteria(IdentityImpl.class);
    criteria.add(Restrictions.or(Restrictions.ilike("externalName", str), Restrictions.ilike("alias", str)));
    @SuppressWarnings("unchecked")
    Collection<Identity> records = criteria.list();
    return fetchSqlStore(records);
  }

  @Override
  public Collection<Identity> getIdentitiesByExternalNameAndProject(String externalName, Long projectId) throws IOException {
    if (isStringEmptyOrNull(externalName)) return Collections.emptySet();
    if (projectId == null) throw new IllegalArgumentException("Must provide a projectId in search");
    String str = DbUtils.convertStringToSearchQuery(externalName);
    Criteria criteria = currentSession().createCriteria(IdentityImpl.class);
    criteria.add(Restrictions.eq("project.id", projectId));
    criteria.add(Restrictions.or(Restrictions.ilike("externalName", str), Restrictions.ilike("alias", str)));
    @SuppressWarnings("unchecked")
    Collection<Identity> records = criteria.list();
    return fetchSqlStore(records);
  }

  @Override
  public Sample getByPreMigrationId(Long id) throws IOException {
    Query query = currentSession().createQuery("FROM DetailedSampleImpl ds WHERE ds.preMigrationId = :id");
    query.setParameter("id", id);
    return fetchSqlStore((Sample) query.uniqueResult());
  }
}
