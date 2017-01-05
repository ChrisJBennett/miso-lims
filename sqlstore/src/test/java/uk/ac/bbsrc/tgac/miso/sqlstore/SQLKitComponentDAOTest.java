package uk.ac.bbsrc.tgac.miso.sqlstore;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.eaglegenomics.simlims.core.User;

import uk.ac.bbsrc.tgac.miso.AbstractDAOTest;
import uk.ac.bbsrc.tgac.miso.core.data.ChangeLog;
import uk.ac.bbsrc.tgac.miso.core.data.KitComponent;
import uk.ac.bbsrc.tgac.miso.core.data.KitComponentDescriptor;
import uk.ac.bbsrc.tgac.miso.core.data.impl.UserImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.kit.KitComponentImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.kit.KitDescriptor;
import uk.ac.bbsrc.tgac.miso.core.data.type.KitType;
import uk.ac.bbsrc.tgac.miso.core.store.ChangeLogStore;
import uk.ac.bbsrc.tgac.miso.core.store.NoteStore;
import uk.ac.bbsrc.tgac.miso.core.store.SecurityStore;

public class SQLKitComponentDAOTest extends AbstractDAOTest {

  @InjectMocks
  private SQLKitComponentDAO dao;

  @Autowired
  @Spy
  private JdbcTemplate jdbcTemplate;

  @Mock
  private NoteStore noteDAO;

  @Mock
  private SecurityStore securityDAO;

  @Mock
  private ChangeLogStore changeLogDAO;

  private final User user = new UserImpl();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    dao.setJdbcTemplate(jdbcTemplate);
    user.setUserId(1L);
    when(securityDAO.getUserById(anyLong())).thenReturn(user);
    when(changeLogDAO.listAllById(anyString(), anyLong())).thenReturn(new ArrayList<ChangeLog>());
  }

  @Test
  public void testGet() throws IOException {
    KitComponent kit = dao.get(1L);
    assertThat(kit.getLocationBarcode(), is("Freezer2"));
  }

  @Test
  public void testGetKitByIdentificationBarcode() throws IOException {

    KitComponent kit = dao.getKitComponentByIdentificationBarcode("5678");
    assertThat(kit.getLotNumber(), is("LOT35"));
  }

  @Test
  public void testGetKitByLotNumber() throws IOException {
    List<KitComponent> kits = dao.listKitComponentsByLotNumber("LOT35");
    if (kits.size() != 1) {
      fail("wrong number of kitComponents returned!");
    }
    KitComponent kit = kits.get(0);
    assertThat(kit.getIdentificationBarcode(), is("5678"));
  }

  @Test
  public void testGetKitByLotNumberNotFound() throws IOException {
    List<KitComponent> kits = dao.listKitComponentsByLotNumber("phantomLOT");
    assertEquals(0, kits.size());
  }

  @Test
  public void testListAll() throws IOException {
    Collection<KitComponent> kits = dao.listAll();
    assertThat(kits.size(), is(2));
  }

  @Test
  public void testCount() throws IOException {
    assertThat("Count of Kits", dao.count(), is(2));
  }

  @Test
  public void testListByExperiment() throws IOException {
    List<KitComponent> kits = dao.listByExperiment(1L);
    assertThat(kits.size(), is(0));
  }

  @Test
  public void testListByManufacturer() throws IOException {
    List<KitComponent> kit = dao.listByManufacturer("Roche");
    assertThat(kit.size(), is(2));
  }

  @Test
  public void testListKitsByType() throws IOException {
    List<KitComponent> kit = dao.listByType(KitType.SEQUENCING);
    assertThat(kit.size(), is(2));
  }

  @Test
  public void testSave() throws IOException {
    KitComponent newKit = makeNewKit();
    assertThat(dao.save(newKit), is(3L));
    KitComponent savedKit = dao.get(3L);
    assertThat(savedKit.getIdentificationBarcode(), is(newKit.getIdentificationBarcode()));
  }

  @Test
  public void testSaveUpdate() throws IOException {
    KitComponent existingKit = dao.get(1L);
    existingKit.setLotNumber("UPDATED");
    assertThat(dao.save(existingKit), is(1L));
    KitComponent updatedKit = dao.get(1L);
    assertThat(updatedKit.getLotNumber(), is("UPDATED"));
  }

  private KitComponent makeNewKit() throws IOException {
    KitComponent kit = new KitComponentImpl();
    kit.setIdentificationBarcode("KittVsCarr");
    KitDescriptor descriptor = Mockito.mock(KitDescriptor.class);
    KitComponentDescriptor componentDescriptor = Mockito.mock(KitComponentDescriptor.class);

    when(descriptor.getId()).thenReturn(1L);
    when(componentDescriptor.getKitDescriptor()).thenReturn(descriptor);

    kit.setKitComponentDescriptor(componentDescriptor);
    return kit;
  }
}
