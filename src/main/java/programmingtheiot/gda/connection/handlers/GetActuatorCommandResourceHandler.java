/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 */

package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;

/**
 * Observable resource handler for actuator commands from GDA to CDA.
 * Implements CoAP OBSERVE specification to notify CDA of actuation updates.
 */
public class GetActuatorCommandResourceHandler extends CoapResource implements IActuatorDataListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(GetActuatorCommandResourceHandler.class.getName());
	
	// params
	
	private ActuatorData actuatorData = null;
	
	// constructors
	
	/**
	 * Constructor.
	 * 
	 * @param resourceName The resource name
	 */
	public GetActuatorCommandResourceHandler(String resourceName)
	{
		super(resourceName);
		
		// Set the resource to be observable
		super.setObservable(true);
		
		// Initialize with default ActuatorData
		this.actuatorData = new ActuatorData();
		
		_Logger.info("Resource handler created with name: " + resourceName);
	}
	
	// public methods - IActuatorDataListener implementation
	
	/**
	 * Callback method for actuator data updates.
	 * 
	 * @param data The actuator data update
	 * @return true if update successful, false otherwise
	 */
	@Override
	public boolean onActuatorDataUpdate(ActuatorData data)
	{
		if (data != null && this.actuatorData != null) {
			this.actuatorData.updateData(data);
			
			// Notify all connected clients (observers)
			super.changed();
			
			_Logger.fine("Actuator data updated for URI: " + super.getURI() + 
				": Data value = " + this.actuatorData.getValue());
			
			return true;
		}
		
		return false;
	}
	
	// CoAP request handlers
	
	/**
	 * Handles GET requests for actuator command data.
	 * This will be called by observers or regular GET requests.
	 */
	@Override
	public void handleGET(CoapExchange context)
	{
		// Accept the request
		context.accept();
		
		_Logger.info("GET request received for ActuatorCommand resource");
		
		// Convert the locally stored ActuatorData to JSON
		String jsonData = 
			DataUtil.getInstance().actuatorDataToJson(this.actuatorData);
		
		// Send response with JSON content
		context.respond(ResponseCode.CONTENT, jsonData);
	}
	
	@Override
	public void handlePUT(CoapExchange context)
	{
		_Logger.info("PUT request received for ActuatorCommand resource");
		
		context.accept();
		context.respond(ResponseCode.CHANGED, "ActuatorCommand PUT handler");
	}
	
	@Override
	public void handlePOST(CoapExchange context)
	{
		_Logger.info("POST request received for ActuatorCommand resource");
		
		context.accept();
		context.respond(ResponseCode.CREATED, "ActuatorCommand POST handler");
	}
	
	@Override
	public void handleDELETE(CoapExchange context)
	{
		_Logger.info("DELETE request received for ActuatorCommand resource");
		
		context.accept();
		context.respond(ResponseCode.DELETED, "ActuatorCommand DELETE handler");
	}
}
