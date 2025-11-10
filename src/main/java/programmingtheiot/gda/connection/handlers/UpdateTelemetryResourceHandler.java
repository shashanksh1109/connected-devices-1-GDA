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

import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
/**
 * Resource handler for sensor/telemetry data updates from CDA to GDA.
 */
public class UpdateTelemetryResourceHandler extends CoapResource
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(UpdateTelemetryResourceHandler.class.getName());
	
	// params
	
	private IDataMessageListener dataMsgListener = null;
	
	// constructors
	
	/**
	 * Constructor.
	 * 
	 * @param resourceName The resource name
	 */
	public UpdateTelemetryResourceHandler(String resourceName)
	{
		super(resourceName);
		_Logger.info("Resource handler created with name: " + resourceName);
	}
	
	// public methods
	
	/**
	 * Sets the data message listener for callbacks.
	 * 
	 * @param listener The data message listener
	 */
	public void setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
		}
	}
	
	// CoAP request handlers
	
	@Override
	public void handleGET(CoapExchange context)
	{
		_Logger.info("GET request received for SensorData resource");
		
		context.accept();
		context.respond(ResponseCode.CONTENT, "SensorData GET handler");
	}
	
	@Override
	public void handlePUT(CoapExchange context)
	{
		ResponseCode code = ResponseCode.NOT_ACCEPTABLE;
		
		context.accept();
		
		if (this.dataMsgListener != null) {
			try {
				String jsonData = new String(context.getRequestPayload());
				
				SensorData sensorData =
					DataUtil.getInstance().jsonToSensorData(jsonData);
				
				this.dataMsgListener.handleSensorMessage(
					ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, sensorData);
				
				code = ResponseCode.CHANGED;
				
				_Logger.info("Sensor data updated: " + sensorData.getName());
			} catch (Exception e) {
				_Logger.warning(
					"Failed to handle PUT request. Message: " + e.getMessage());
				
				code = ResponseCode.BAD_REQUEST;
			}
		} else {
			_Logger.info("No callback listener for request. Ignoring PUT.");
			code = ResponseCode.CONTINUE;
		}
		
		String msg = "Update sensor data request handled: " + super.getName();
		context.respond(code, msg);
	}
	
	@Override
	public void handlePOST(CoapExchange context)
	{
		_Logger.info("POST request received for SensorData resource");
		
		context.accept();
		context.respond(ResponseCode.CREATED, "SensorData POST handler");
	}
	
	@Override
	public void handleDELETE(CoapExchange context)
	{
		_Logger.info("DELETE request received for SensorData resource");
		
		context.accept();
		context.respond(ResponseCode.DELETED, "SensorData DELETE handler");
	}
}