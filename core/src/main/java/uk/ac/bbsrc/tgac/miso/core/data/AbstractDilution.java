/*
 * Copyright (c) 2012. The Genome Analysis Centre, Norwich, UK
 * MISO project contacts: Robert Davey @ TGAC
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

package uk.ac.bbsrc.tgac.miso.core.data;

import java.util.Date;

import javax.persistence.CascadeType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import com.eaglegenomics.simlims.core.SecurityProfile;
import com.eaglegenomics.simlims.core.User;

import uk.ac.bbsrc.tgac.miso.core.security.SecurableByProfile;
import uk.ac.bbsrc.tgac.miso.core.util.CoverageIgnore;

/**
 * Skeleton implementation of a Dilution
 * 
 * @author Rob Davey
 * @since 0.0.2
 */
public abstract class AbstractDilution implements Dilution, Comparable {
  public static final Long UNSAVED_ID = 0L;

  @OneToOne(cascade = CascadeType.ALL)
  private SecurityProfile securityProfile;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long dilutionId = AbstractDilution.UNSAVED_ID;
  private String name;
  private Date creationDate;
  private Double concentration;
  private String identificationBarcode;
  private String dilutionUserName;
  private Long preMigrationId;

  @Override
  public long getId() {
    return dilutionId;
  }

  public void setId(long id) {
    this.dilutionId = id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getDilutionCreator() {
    return dilutionUserName;
  }

  @Override
  public void setDilutionCreator(String dilutionUserName) {
    this.dilutionUserName = dilutionUserName;
  }

  @Override
  public Date getCreationDate() {
    return this.creationDate;
  }

  @Override
  public void setCreationDate(Date creationDate) {
    this.creationDate = creationDate;
  }

  @Override
  public Double getConcentration() {
    return this.concentration;
  }

  @Override
  public void setConcentration(Double concentration) {
    this.concentration = concentration;
  }

  @Override
  public String getIdentificationBarcode() {
    return this.identificationBarcode;
  }

  @Override
  public void setIdentificationBarcode(String identificationBarcode) {
    this.identificationBarcode = identificationBarcode;
  }

  @Override
  @CoverageIgnore
  public String getLabelText() {
    return getLibrary().getAlias();
  }

  @Override
  public SecurityProfile getSecurityProfile() {
    return securityProfile;
  }

  @Override
  public void setSecurityProfile(SecurityProfile profile) {
    this.securityProfile = profile;
  }

  @Override
  public Long getPreMigrationId() {
    return preMigrationId;
  }

  @Override
  public void setPreMigrationId(Long preMigrationId) {
    this.preMigrationId = preMigrationId;
  }

  @Override
  public void inheritPermissions(SecurableByProfile parent) throws SecurityException {
    if (parent.getSecurityProfile().getOwner() != null) {
      setSecurityProfile(parent.getSecurityProfile());
    } else {
      throw new SecurityException("Cannot inherit permissions when parent object owner is not set!");
    }
  }

  @CoverageIgnore
  @Override
  public boolean userCanRead(User user) {
    return securityProfile.userCanRead(user);
  }

  @CoverageIgnore
  @Override
  public boolean userCanWrite(User user) {
    return securityProfile.userCanWrite(user);
  }

  @CoverageIgnore
  @Override
  public boolean isDeletable() {
    return getId() != AbstractDilution.UNSAVED_ID;
  }

  @CoverageIgnore
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(getId());
    sb.append(" : ");
    sb.append(getName());
    sb.append(" : ");
    sb.append(getCreationDate());
    sb.append(" : ");
    sb.append(getConcentration());
    return sb.toString();
  }

  /**
   * Equivalency is based on getProjectId() if set, otherwise on name, description and creation date.
   */
  @CoverageIgnore
  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj == this) return true;
    if (!(obj instanceof Dilution)) return false;
    final Dilution them = (Dilution) obj;
    // If not saved, then compare resolved actual objects. Otherwise
    // just compare IDs.
    if (AbstractDilution.UNSAVED_ID == getId() || AbstractDilution.UNSAVED_ID == them.getId()) {
      if (name == null) {
        if (them.getName() != null)
          return false;
      }
      else if (!name.equals(them.getName()))
        return false;
      if (identificationBarcode == null) {
        if (them.getIdentificationBarcode() != null)
          return false;
      }
      else if (!identificationBarcode.equals(them.getIdentificationBarcode()))
        return false;
      if (concentration == null) {
        if (them.getConcentration() != null)
          return false;
      }
      else if (!concentration.equals(them.getConcentration()))
        return false;
      if (creationDate == null) {
        if (them.getCreationDate() != null)
          return false;
      }
      else if (!creationDate.equals(them.getCreationDate()))
        return false;
      if (dilutionUserName == null) {
        if (them.getDilutionCreator() != null)
          return false;
      }
      else if (!dilutionUserName.equals(them.getDilutionCreator()))
        return false;
      return true;
    } else {
      return getId() == them.getId();
    }
  }

  @CoverageIgnore
  @Override
  public int hashCode() {
    if (AbstractDilution.UNSAVED_ID != getId()) {
      return (int) getId();
    } else {
      final int PRIME = 37;
      int hashcode = 1;
      if (getCreationDate() != null) hashcode = PRIME * hashcode + getCreationDate().hashCode();
      if (getDilutionCreator() != null) hashcode = PRIME * hashcode + getDilutionCreator().hashCode();
      if (getConcentration() != null) hashcode = PRIME * hashcode + getConcentration().hashCode();
      return hashcode;
    }
  }

  @CoverageIgnore
  @Override
  public int compareTo(Object o) {
    final Dilution t = (Dilution) o;
    if (getId() < t.getId()) return -1;
    if (getId() > t.getId()) return 1;
    return 0;
  }
}
