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

package uk.ac.bbsrc.tgac.miso.core.service.integration.ws.solid;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.bind.annotation.XmlSeeAlso;

/**
 * This class was generated by the JAX-WS RI. JAX-WS RI 2.1.6 in JDK 6 Generated source version: 2.1
 * 
 */
@WebService(name = "Solid", targetNamespace = "http://solid.aga.appliedbiosystems.com")
@SOAPBinding(style = SOAPBinding.Style.RPC)
@XmlSeeAlso({ ObjectFactory.class })
public interface Solid {

  /**
   * 
   * @return returns uk.ac.bbsrc.tgac.miso.webapp.service.integration.solid.ws.ClusterStatus
   */
  @WebMethod
  @WebResult(partName = "return")
  public ClusterStatus getClusterStatus();

  /**
   * 
   * @param cleared
   * @param startDate
   * @param component
   * @param endDate
   * @param severity
   * @return returns uk.ac.bbsrc.tgac.miso.webapp.service.integration.solid.ws.LogMessageArray
   */
  @WebMethod
  @WebResult(partName = "return")
  public LogMessageArray getLogMessages(@WebParam(name = "severity", partName = "severity") String severity,
      @WebParam(name = "component", partName = "component") String component,
      @WebParam(name = "startDate", partName = "startDate") String startDate,
      @WebParam(name = "endDate", partName = "endDate") String endDate, @WebParam(name = "cleared", partName = "cleared") String cleared);

  /**
   * 
   * @param arg1
   * @param arg0
   * @return returns uk.ac.bbsrc.tgac.miso.webapp.service.integration.solid.ws.RunArray
   */
  @WebMethod
  @WebResult(partName = "return")
  public RunArray getRun(@WebParam(name = "arg0", partName = "arg0") String arg0, @WebParam(name = "arg1", partName = "arg1") String arg1);

  /**
   * 
   * @return returns uk.ac.bbsrc.tgac.miso.webapp.service.integration.solid.ws.RunArray
   */
  @WebMethod
  @WebResult(partName = "return")
  public RunArray getLastStartedRuns();

  /**
   * 
   * @param arg0
   * @return returns uk.ac.bbsrc.tgac.miso.webapp.service.integration.solid.ws.RunArray
   */
  @WebMethod
  @WebResult(partName = "return")
  public RunArray getRunsByStatus(@WebParam(name = "arg0", partName = "arg0") String arg0);

}
