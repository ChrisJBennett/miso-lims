/*
 * Copyright (c) 2012. The Genome Analysis Centre, Norwich, UK
 * MISO project contacts: Robert Davey, Mario Caccamo @ TGAC
 * *********************************************************************
 *
 * This file is part of MISO.
 *
 * MISO is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MISO is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MISO.  If not, see <http://www.gnu.org/licenses/>.
 *
 * *********************************************************************
 */

package uk.ac.bbsrc.tgac.miso.webapp.controller;

import static uk.ac.bbsrc.tgac.miso.core.util.LimsUtils.isStringEmptyOrNull;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import com.eaglegenomics.simlims.core.SecurityProfile;
import com.eaglegenomics.simlims.core.User;
import com.eaglegenomics.simlims.core.manager.SecurityManager;

import uk.ac.bbsrc.tgac.miso.core.data.AbstractPool;
import uk.ac.bbsrc.tgac.miso.core.data.AbstractSample;
import uk.ac.bbsrc.tgac.miso.core.data.AbstractSampleQC;
import uk.ac.bbsrc.tgac.miso.core.data.ChangeLog;
import uk.ac.bbsrc.tgac.miso.core.data.EntityGroup;
import uk.ac.bbsrc.tgac.miso.core.data.Experiment;
import uk.ac.bbsrc.tgac.miso.core.data.Identity.DonorSex;
import uk.ac.bbsrc.tgac.miso.core.data.Lab;
import uk.ac.bbsrc.tgac.miso.core.data.Library;
import uk.ac.bbsrc.tgac.miso.core.data.Nameable;
import uk.ac.bbsrc.tgac.miso.core.data.Pool;
import uk.ac.bbsrc.tgac.miso.core.data.Poolable;
import uk.ac.bbsrc.tgac.miso.core.data.Project;
import uk.ac.bbsrc.tgac.miso.core.data.QcPassedDetail;
import uk.ac.bbsrc.tgac.miso.core.data.Run;
import uk.ac.bbsrc.tgac.miso.core.data.Sample;
import uk.ac.bbsrc.tgac.miso.core.data.SampleAdditionalInfo;
import uk.ac.bbsrc.tgac.miso.core.data.SampleClass;
import uk.ac.bbsrc.tgac.miso.core.data.SamplePurpose;
import uk.ac.bbsrc.tgac.miso.core.data.SampleStock;
import uk.ac.bbsrc.tgac.miso.core.data.SampleTissue;
import uk.ac.bbsrc.tgac.miso.core.data.Subproject;
import uk.ac.bbsrc.tgac.miso.core.data.TissueMaterial;
import uk.ac.bbsrc.tgac.miso.core.data.TissueOrigin;
import uk.ac.bbsrc.tgac.miso.core.data.TissueType;
import uk.ac.bbsrc.tgac.miso.core.data.impl.DetailedSampleBuilder;
import uk.ac.bbsrc.tgac.miso.core.data.impl.LabImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.LibraryDilution;
import uk.ac.bbsrc.tgac.miso.core.data.impl.ProjectImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.QcPassedDetailImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.SampleClassImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.SamplePurposeImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.SubprojectImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.TissueMaterialImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.TissueOriginImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.TissueTypeImpl;
import uk.ac.bbsrc.tgac.miso.core.data.type.QcType;
import uk.ac.bbsrc.tgac.miso.core.data.type.StrStatus;
import uk.ac.bbsrc.tgac.miso.core.exception.MalformedSampleException;
import uk.ac.bbsrc.tgac.miso.core.factory.DataObjectFactory;
import uk.ac.bbsrc.tgac.miso.core.manager.RequestManager;
import uk.ac.bbsrc.tgac.miso.core.security.util.LimsSecurityUtils;
import uk.ac.bbsrc.tgac.miso.core.service.naming.MisoNamingScheme;
import uk.ac.bbsrc.tgac.miso.core.util.LimsUtils;
import uk.ac.bbsrc.tgac.miso.dto.Dtos;
import uk.ac.bbsrc.tgac.miso.dto.SampleDto;
import uk.ac.bbsrc.tgac.miso.service.LabService;
import uk.ac.bbsrc.tgac.miso.service.QcPassedDetailService;
import uk.ac.bbsrc.tgac.miso.service.SampleClassService;
import uk.ac.bbsrc.tgac.miso.service.SamplePurposeService;
import uk.ac.bbsrc.tgac.miso.service.SampleService;
import uk.ac.bbsrc.tgac.miso.service.TissueMaterialService;
import uk.ac.bbsrc.tgac.miso.service.TissueOriginService;
import uk.ac.bbsrc.tgac.miso.service.TissueTypeService;
import uk.ac.bbsrc.tgac.miso.webapp.controller.rest.ui.SampleOptionsController;

@Controller
@RequestMapping("/sample")
@SessionAttributes("sample")
public class EditSampleController {
  protected static final Logger log = LoggerFactory.getLogger(EditSampleController.class);

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired
  private SecurityManager securityManager;

  @Autowired
  private RequestManager requestManager;

  @Autowired
  private DataObjectFactory dataObjectFactory;

  @Autowired
  private MisoNamingScheme<Sample> sampleNamingScheme;

  @Autowired
  private SampleOptionsController sampleOptionsController;

  @Autowired
  private SampleService sampleService;

  public void setSampleOptionsController(SampleOptionsController sampleOptionsController) {
    this.sampleOptionsController = sampleOptionsController;
  }

  public void setDataObjectFactory(DataObjectFactory dataObjectFactory) {
    this.dataObjectFactory = dataObjectFactory;
  }

  public void setRequestManager(RequestManager requestManager) {
    this.requestManager = requestManager;
  }

  public void setSecurityManager(SecurityManager securityManager) {
    this.securityManager = securityManager;
  }

  @ModelAttribute("aliasGenerationEnabled")
  public Boolean isAliasGenerationEnabled() {
    return sampleNamingScheme != null && sampleNamingScheme.hasGeneratorFor("alias");
  }

  @Value("${miso.notification.interop.enabled}")
  private Boolean metrixEnabled;
  @Value("${miso.autoGenerateIdentificationBarcodes}")
  private Boolean autoGenerateIdBarcodes;
  @Value("${miso.detailed.sample.enabled}")
  private Boolean detailedSample;

  @ModelAttribute("metrixEnabled")
  public Boolean isMetrixEnabled() {
    return metrixEnabled;
  }

  @ModelAttribute("autoGenerateIdBarcodes")
  public Boolean autoGenerateIdentificationBarcodes() {
    return autoGenerateIdBarcodes;
  }

  @ModelAttribute("detailedSample")
  public Boolean isDetailedSampleEnabled() {
    return detailedSample;
  }

  @ModelAttribute("sampleOptions")
  public String getSampleOptions(UriComponentsBuilder uriBuilder, HttpServletResponse response) throws IOException {
    return mapper.writeValueAsString(sampleOptionsController.getSampleOptions(uriBuilder, response));
  }

  public Map<String, Sample> getAdjacentSamplesInGroup(Sample s, @RequestParam(value = "entityGroupId", required = true) Long entityGroupId)
      throws IOException {
    Project p = s.getProject();
    EntityGroup<? extends Nameable, Sample> sgroup = (EntityGroup<? extends Nameable, Sample>) requestManager
        .getEntityGroupById(entityGroupId);

    Sample prevS = null;
    Sample nextS = null;

    if (p != null) {
      if (!sgroup.getEntities().isEmpty()) {
        Map<String, Sample> ret = new HashMap<>();
        List<Sample> ss = new ArrayList<>(sgroup.getEntities());
        Collections.sort(ss);
        for (int i = 0; i < ss.size(); i++) {
          if (ss.get(i).equals(s)) {
            if (i != 0 && ss.get(i - 1) != null) {
              prevS = ss.get(i - 1);
            }

            if (i != ss.size() - 1 && ss.get(i + 1) != null) {
              nextS = ss.get(i + 1);
            }
            break;
          }
        }
        ret.put("previousSample", prevS);
        ret.put("nextSample", nextS);
        return ret;
      }
    }
    return Collections.emptyMap();
  }

  public Map<String, Sample> getAdjacentSamplesInProject(Sample s, @RequestParam(value = "projectId", required = false) Long projectId)
      throws IOException {
    Project p = s.getProject();
    Sample prevS = null;
    Sample nextS = null;

    if (p != null && p.getId() == projectId) {
      if (!p.getSamples().isEmpty()) {
        Map<String, Sample> ret = new HashMap<>();
        List<Sample> ss = new ArrayList<>(p.getSamples());
        Collections.sort(ss);
        for (int i = 0; i < ss.size(); i++) {
          if (ss.get(i).equals(s)) {
            if (i != 0 && ss.get(i - 1) != null) {
              prevS = ss.get(i - 1);
            }

            if (i != ss.size() - 1 && ss.get(i + 1) != null) {
              nextS = ss.get(i + 1);
            }
            break;
          }
        }
        ret.put("previousSample", prevS);
        ret.put("nextSample", nextS);
        return ret;
      }
    }
    return Collections.emptyMap();
  }

  public Collection<Project> populateProjects(@RequestParam(value = "projectId", required = false) Long projectId) throws IOException {
    try {
      if (projectId != null) {
        Collection<Project> ps = new ArrayList<Project>();
        for (Project p : requestManager.listAllProjects()) {
          if (!p.getProjectId().equals(projectId)) {
            ps.add(p);
          }
        }
        return ps;
      }
      return requestManager.listAllProjects();
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to list projects", ex);
      }
      throw ex;
    }
  }

  public Experiment populateExperiment(@RequestParam(value = "experimentId", required = false) Long experimentId) throws IOException {
    try {
      if (experimentId != null) {
        return requestManager.getExperimentById(experimentId);
      } else {
        return dataObjectFactory.getExperiment();
      }
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to get parent experiment", ex);
      }
      throw ex;
    }
  }

  private Set<Pool<? extends Poolable<?, ?>>> getPoolsBySample(Sample s) throws IOException {
    if (!s.getLibraries().isEmpty()) {
      Set<Pool<? extends Poolable<?, ?>>> pools = new TreeSet<>();
      for (Library l : s.getLibraries()) {
        List<Pool<? extends Poolable<?, ?>>> prs = new ArrayList<>(requestManager.listPoolsByLibraryId(l.getId()));
        pools.addAll(prs);
      }
      return pools;
    }
    return Collections.emptySet();
  }

  private Set<Run> getRunsBySamplePools(Set<Pool<? extends Poolable<?, ?>>> pools) throws IOException {
    if (!pools.isEmpty()) {
      Set<Run> runs = new TreeSet<>();
      for (Pool<? extends Poolable<?, ?>> pool : pools) {
        Collection<Run> prs = requestManager.listRunsByPoolId(pool.getId());
        runs.addAll(prs);
      }
      return runs;
    }
    return Collections.emptySet();
  }

  @ModelAttribute("maxLengths")
  public Map<String, Integer> maxLengths() throws IOException {
    return requestManager.getSampleColumnSizes();
  }

  @ModelAttribute("sampleTypesString")
  public String sampleTypesString() throws IOException {
    List<String> types = new ArrayList<String>();
    List<String> sampleTypes = new ArrayList<String>(requestManager.listAllSampleTypes());
    Collections.sort(sampleTypes);
    for (String s : sampleTypes) {
      types.add("\"" + s + "\"" + ":" + "\"" + s + "\"");
    }
    return LimsUtils.join(types, ",");
  }

  @ModelAttribute("sampleQCUnits")
  public String sampleQCUnits() throws IOException {
    return AbstractSampleQC.UNITS;
  }

  @ModelAttribute("libraryDilutionUnits")
  public String libraryDilutionUnits() {
    return LibraryDilution.UNITS;
  }

  @ModelAttribute("poolConcentrationUnits")
  public String poolConcentrationUnits() {
    return AbstractPool.CONCENTRATION_UNITS;
  }

  @ModelAttribute("libraryQcTypesString")
  public String libraryTypesString() throws IOException {
    List<String> types = new ArrayList<String>();
    List<QcType> libraryQcTypes = new ArrayList<QcType>(requestManager.listAllLibraryQcTypes());
    Collections.sort(libraryQcTypes);
    for (QcType s : libraryQcTypes) {
      types.add("\"" + s.getQcTypeId() + "\"" + ":" + "\"" + s.getName() + "\"");
    }
    return LimsUtils.join(types, ",");
  }

  // Handsontable
  @ModelAttribute("referenceDataJSON")
  public JSONObject referenceDataJsonString() throws IOException, JSONException {
    final JSONObject hot = new JSONObject();
    final List<String> sampleTypes = new ArrayList<String>(requestManager.listAllSampleTypes());
    final List<String> strStatuses = new ArrayList<String>();
    final List<String> donorSexes = new ArrayList<String>();
    JSONArray allProjects = new JSONArray();
    for (Project fullProject : requestManager.listAllProjects()) {
      JSONObject project = new JSONObject();
      project.put("id", fullProject.getId());
      project.put("alias", fullProject.getAlias());
      project.put("name", fullProject.getName());
      allProjects.put(project);
    }
    for (String strLabel : StrStatus.getLabels()) {
      strStatuses.add(strLabel);
    }
    for (String dsLabel : DonorSex.getLabels()) {
      donorSexes.add(dsLabel);
    }

    hot.put("sampleTypes", sampleTypes);
    hot.put("projects", allProjects);
    hot.put("strStatuses", strStatuses);
    hot.put("donorSexes", donorSexes);

    return hot;
  }

  @Autowired
  private SampleClassService sampleClassService;

  private static final Comparator<SampleClass> SAMPLECLASS_ALIAS = new Comparator<SampleClass>() {
    @Override
    public int compare(SampleClass o1, SampleClass o2) {
      return o1.getAlias().compareTo(o2.getAlias());
    }
  };

  private void populateSampleClasses(ModelMap model) throws IOException {
    List<SampleClass> sampleClasses = new ArrayList<>();
    List<SampleClass> tissueClasses = new ArrayList<>();
    // Can only create Tissues and Analyte Stock from this page, so remove other classes
    for (SampleClass sc : sampleClassService.getAll()) {
      if (SampleTissue.CATEGORY_NAME.equals(sc.getSampleCategory())) {
        tissueClasses.add(sc);
        sampleClasses.add(sc);
      } else if (SampleStock.CATEGORY_NAME.equals(sc.getSampleCategory())) {
        sampleClasses.add(sc);
      }
    }
    Collections.sort(sampleClasses, SAMPLECLASS_ALIAS);
    Collections.sort(tissueClasses, SAMPLECLASS_ALIAS);
    model.put("sampleClasses", sampleClasses);
    model.put("tissueClasses", tissueClasses);
  }

  @Autowired
  private TissueOriginService tissueOriginService;

  @ModelAttribute("tissueOrigins")
  public List<TissueOrigin> getTissueOrigins() throws IOException {
    List<TissueOrigin> list = new ArrayList<>(tissueOriginService.getAll());
    Collections.sort(list, new Comparator<TissueOrigin>() {
      @Override
      public int compare(TissueOrigin o1, TissueOrigin o2) {
        return o1.getDescription().compareTo(o2.getDescription());
      }
    });
    return list;
  }

  @Autowired
  private TissueTypeService tissueTypeService;

  @ModelAttribute("tissueTypes")
  public List<TissueType> getTissueTypes() throws IOException {
    List<TissueType> list = new ArrayList<>(tissueTypeService.getAll());
    Collections.sort(list, new Comparator<TissueType>() {
      @Override
      public int compare(TissueType o1, TissueType o2) {
        return o1.getDescription().compareTo(o2.getDescription());
      }
    });
    return list;
  }

  @Autowired
  private QcPassedDetailService qcpassedDetailService;

  @ModelAttribute("qcPassedDetails")
  public List<QcPassedDetail> getQcPassedDetails() throws IOException {
    List<QcPassedDetail> list = new ArrayList<>(qcpassedDetailService.getAll());
    Collections.sort(list, new Comparator<QcPassedDetail>() {
      @Override
      public int compare(QcPassedDetail o1, QcPassedDetail o2) {
        return o1.getDescription().compareTo(o2.getDescription());
      }
    });
    return list;
  }

  @Autowired
  private LabService labService;

  @ModelAttribute("labs")
  public List<Lab> getLabs() throws IOException {
    List<Lab> list = new ArrayList<>(labService.getAll());
    Collections.sort(list, new Comparator<Lab>() {
      @Override
      public int compare(Lab o1, Lab o2) {
        return o1.getAlias().compareTo(o2.getAlias());
      }
    });
    return list;
  }

  @Autowired
  private SamplePurposeService samplePurposeService;

  @ModelAttribute("samplePurposes")
  public List<SamplePurpose> getSamplePurposes() throws IOException {
    List<SamplePurpose> list = new ArrayList<>(samplePurposeService.getAll());
    Collections.sort(list, new Comparator<SamplePurpose>() {
      @Override
      public int compare(SamplePurpose o1, SamplePurpose o2) {
        return o1.getAlias().compareTo(o2.getAlias());
      }
    });
    return list;
  }

  @Autowired
  private TissueMaterialService tissueMaterialService;

  @ModelAttribute("tissueMaterials")
  public List<TissueMaterial> getTissueMaterials() throws IOException {
    List<TissueMaterial> list = new ArrayList<>(tissueMaterialService.getAll());
    Collections.sort(list, new Comparator<TissueMaterial>() {
      @Override
      public int compare(TissueMaterial o1, TissueMaterial o2) {
        return o1.getAlias().compareTo(o2.getAlias());
      }
    });
    return list;
  }

  @ModelAttribute("strStatusOptions")
  public StrStatus[] getStrStatusOptions() {
    return StrStatus.values();
  }

  @ModelAttribute("donorSexOptions")
  public DonorSex[] getDonorSexOptions() {
    return DonorSex.values();
  }

  /**
   * Translates foreign keys to entity objects with only the ID set, to be used in service layer to reload persisted child objects
   * 
   * @param binder
   */
  @InitBinder
  public void includeForeignKeys(WebDataBinder binder) {
    binder.registerCustomEditor(Project.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        Project p = new ProjectImpl();
        p.setId(Long.valueOf(text));
        setValue(p);
      }
    });

    binder.registerCustomEditor(SampleClass.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        if (isStringEmptyOrNull(text)) {
          setValue(null);
        } else {
          SampleClass sc = new SampleClassImpl();
          sc.setId(Long.valueOf(text));
          setValue(sc);
        }
      }
    });

    binder.registerCustomEditor(TissueOrigin.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        TissueOrigin to = new TissueOriginImpl();
        to.setId(Long.valueOf(text));
        setValue(to);
      }
    });

    binder.registerCustomEditor(TissueType.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        TissueType tt = new TissueTypeImpl();
        tt.setId(Long.valueOf(text));
        setValue(tt);
      }
    });

    binder.registerCustomEditor(QcPassedDetail.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        if (isStringEmptyOrNull(text)) {
          setValue(null);
        } else {
          QcPassedDetail qcpd = new QcPassedDetailImpl();
          qcpd.setId(Long.valueOf(text));
          setValue(qcpd);
        }
      }
    });

    binder.registerCustomEditor(Subproject.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        if (isStringEmptyOrNull(text)) {
          setValue(null);
        } else {
          Subproject sp = new SubprojectImpl();
          sp.setId(Long.valueOf(text));
          setValue(sp);
        }
      }
    });

    binder.registerCustomEditor(Lab.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        if (isStringEmptyOrNull(text)) {
          setValue(null);
        } else {
          Lab lab = new LabImpl();
          lab.setId(Long.valueOf(text));
          setValue(lab);
        }
      }
    });

    binder.registerCustomEditor(SamplePurpose.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        if (isStringEmptyOrNull(text)) {
          setValue(null);
        } else {
          SamplePurpose sp = new SamplePurposeImpl();
          sp.setId(Long.valueOf(text));
          setValue(sp);
        }
      }
    });

    binder.registerCustomEditor(TissueMaterial.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        if (isStringEmptyOrNull(text)) {
          setValue(null);
        } else {
          TissueMaterial tm = new TissueMaterialImpl();
          tm.setId(Long.valueOf(text));
          setValue(tm);
        }
      }
    });

    binder.registerCustomEditor(Long.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) throws IllegalArgumentException {
        setValue(isStringEmptyOrNull(text) ? null : Long.valueOf(text));
      }
    });
  }

  @RequestMapping(value = "/new", method = RequestMethod.GET)
  public ModelAndView newUnassignedSample(ModelMap model) throws IOException {
    return setupForm(AbstractSample.UNSAVED_ID, null, model);
  }

  @RequestMapping(value = "/new/{projectId}", method = RequestMethod.GET)
  public ModelAndView newAssignedSample(@PathVariable Long projectId, ModelMap model) throws IOException {
    return setupForm(AbstractSample.UNSAVED_ID, projectId, model);
  }

  @RequestMapping(value = "/rest/{sampleId}", method = RequestMethod.GET)
  public @ResponseBody Sample jsonRest(@PathVariable Long sampleId) throws IOException {
    return requestManager.getSampleById(sampleId);
  }

  @RequestMapping(value = "/{sampleId}", method = RequestMethod.GET)
  public ModelAndView setupForm(@PathVariable Long sampleId, ModelMap model) throws IOException {
    return setupForm(sampleId, null, model);
  }

  @RequestMapping(value = "/{sampleId}/project/{projectId}", method = RequestMethod.GET)
  public ModelAndView setupForm(@PathVariable Long sampleId, @PathVariable Long projectId, ModelMap model) throws IOException {
    try {
      User user = securityManager.getUserByLoginName(SecurityContextHolder.getContext().getAuthentication().getName());
      Sample sample = null;
      if (sampleId == AbstractSample.UNSAVED_ID) {
        sample = detailedSample ? new DetailedSampleBuilder(user) : dataObjectFactory.getSample(user);
        model.put("sampleCategory", "new");
        model.put("title", "New Sample");

        if (projectId != null) {
          Project project = requestManager.lazyGetProjectById(projectId);
          if (project == null) throw new SecurityException("No such project.");
          model.addAttribute("project", project);
          sample.setProject(project);

          if (Arrays.asList(user.getRoles()).contains("ROLE_TECH")) {
            SecurityProfile sp = new SecurityProfile(user);
            LimsUtils.inheritUsersAndGroups(sample, project.getSecurityProfile());
            sp.setOwner(user);
            sample.setSecurityProfile(sp);
          } else {
            sample.inheritPermissions(project);
          }
        } else {
          model.put("accessibleProjects", populateProjects(null));
        }
      } else {
        sample = requestManager.getSampleById(sampleId);
        if (sample == null) throw new SecurityException("No such sample.");
        model.put("sampleCategory", detailedSample ? ((SampleAdditionalInfo) sample).getSampleClass().getSampleCategory() : "plain");
        if (detailedSample) {
          model.put("sampleClass", ((SampleAdditionalInfo) sample).getSampleClass().getAlias());
        }
        model.put("title", "Sample " + sampleId);

        if (projectId != null) {
          Project project = requestManager.lazyGetProjectById(projectId);
          if (project == null) throw new SecurityException("No such project.");
          model.addAttribute("project", project);
          sample.setProject(project);
          sample.inheritPermissions(project);

          Map<String, Sample> adjacentSamples = getAdjacentSamplesInProject(sample, sample.getProject().getProjectId());
          if (!adjacentSamples.isEmpty()) {
            model.put("previousSample", adjacentSamples.get("previousSample"));
            model.put("nextSample", adjacentSamples.get("nextSample"));
          }
        } else {
          model.put("accessibleProjects", populateProjects(null));
        }

        Set<Pool<? extends Poolable<?, ?>>> pools = getPoolsBySample(sample);
        Map<Long, Sample> poolSampleMap = new HashMap<>();
        for (Pool pool : pools) {
          poolSampleMap.put(pool.getId(), sample);
        }
        model.put("poolSampleMap", poolSampleMap);
        model.put("samplePools", pools);
        model.put("sampleRuns", getRunsBySamplePools(pools));
      }

      if (sample != null && !sample.userCanWrite(user)) {
        throw new SecurityException("Permission denied.");
      }

      model.put("formObj", sample);
      model.put("sample", sample);
      model.put("sampleTypes", requestManager.listAllSampleTypes());

      model.put("owners", LimsSecurityUtils.getPotentialOwners(user, sample, securityManager.listAllUsers()));
      model.put("accessibleUsers", LimsSecurityUtils.getAccessibleUsers(user, sample, securityManager.listAllUsers()));
      model.put("accessibleGroups", LimsSecurityUtils.getAccessibleGroups(user, sample, securityManager.listAllGroups()));
      populateSampleClasses(model);

      return new ModelAndView("/pages/editSample.jsp", model);
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to show sample", ex);
      }
      throw ex;
    }
  }

  @RequestMapping(value = "/rest/changes", method = RequestMethod.GET)
  public @ResponseBody Collection<ChangeLog> jsonRestChanges() throws IOException {
    return requestManager.listAllChanges("Sample");
  }

  @RequestMapping(value = "/bulk/dummy", method = RequestMethod.POST)
  public String processSubmit() {
    return null;
  }

  /**
   * used to edit samples with ids from given {sampleIds} sends Dtos objects which will then be used for editing in grid
   */
  @RequestMapping(value = "/bulk/edit/{sampleIds}", method = RequestMethod.GET)
  public ModelAndView editBulkSamples(@PathVariable String sampleIds, ModelMap model) throws IOException {
    try {
      String[] split = sampleIds.split(",");
      List<Long> idList = new ArrayList<Long>();
      for (int i = 0; i < split.length; i++) {
        idList.add(Long.parseLong(split[i]));
      }
      ObjectMapper mapper = new ObjectMapper();
      List<SampleDto> samplesDtos = new ArrayList<>();
      for (Sample sample : requestManager.getSamplesByIdList(idList)) {
        samplesDtos.add(Dtos.asDto(sample));
      }
      model.put("title", "Bulk Edit Samples");
      model.put("samplesJSON", mapper.writeValueAsString(samplesDtos));
      model.put("method", "Edit");
      return new ModelAndView("/pages/bulkEditSamples.jsp", model);
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.error("Failed to get bulk samples", ex);
      }
      throw ex;
    }
  }

  /**
   * used to create new samples parented to samples with ids from given {sampleIds} sends Dtos objects which will then be used for editing
   * in grid
   */
  @RequestMapping(value = "/bulk/create/{sampleIds}&scid={sampleClassId}", method = RequestMethod.GET)
  public ModelAndView createBulkSamples(@PathVariable String sampleIds, @PathVariable Long sampleClassId, ModelMap model)
      throws IOException {
    try {
      String[] split = sampleIds.split(",");
      List<Long> idList = new ArrayList<Long>();
      for (int i = 0; i < split.length; i++) {
        idList.add(Long.parseLong(split[i]));
      }
      Set<SampleDto> samplesDtos = new HashSet<>();
      for (Sample sample : requestManager.getSamplesByIdList(idList)) {
        samplesDtos.add(Dtos.asDto(sample));
      }
      model.put("title", "Bulk Create Samples");
      model.put("samplesJSON", mapper.writeValueAsString(samplesDtos));
      model.put("method", "Create");
      model.put("sampleClassId", sampleClassId);
      return new ModelAndView("/pages/bulkEditSamples.jsp", model);
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.error("Failed to get bulk samples", ex);
      }
      throw ex;
    }
  }

  @RequestMapping(method = RequestMethod.POST)
  public String processSubmit(@ModelAttribute("sample") Sample sample, ModelMap model, SessionStatus session)
      throws IOException, MalformedSampleException {
    if (sample instanceof DetailedSampleBuilder) {
      DetailedSampleBuilder builder = (DetailedSampleBuilder) sample;
      builder.setSampleClass(sampleClassService.get(builder.getSampleClass().getId()));
      if (builder.getTissueClass() != null) {
        builder.setTissueClass(sampleClassService.get(builder.getTissueClass().getId()));
      }
      sample = builder.build();
    }
    try {
      if (sample.getId() == Sample.UNSAVED_ID) {
        sampleService.create(sample);
      } else {
        sampleService.update(sample);
      }
      session.setComplete();
      model.clear();
      return "redirect:/miso/sample/" + sample.getId();
    } catch (IOException ex) {
      log.debug("Failed to save sample", ex);
      throw ex;
    }
  }

}
