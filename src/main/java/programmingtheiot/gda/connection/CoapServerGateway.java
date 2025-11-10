/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * You may find it more helpful to your design to adjust the
 * functionality, constants and interfaces (if there are any)
 * provided within in order to meet the needs of your specific
 * Programming the Internet of Things project.
 */
/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 */

package programmingtheiot.gda.connection;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.interceptors.MessageTracer;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.Configuration;

import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.handlers.GetActuatorCommandResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateSystemPerformanceResourceHandler;
import programmingtheiot.gda.connection.handlers.UpdateTelemetryResourceHandler;

/**
 * CoAP server gateway for GDA.
 */
public class CoapServerGateway
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(CoapServerGateway.class.getName());
	
	// Static initializer for Californium 3.8.0+
	static {
		try {
			Configuration.createStandardWithoutFile();
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Californium configuration initialization issue.", e);
		}
	}
	
	// params
	
	private CoapServer coapServer = null;
	private IDataMessageListener dataMsgListener = null;
	
	// constructors
	
	/**
	 * Constructor.
	 * 
	 * @param dataMsgListener The data message listener for callbacks
	 */
	public CoapServerGateway(IDataMessageListener dataMsgListener)
	{
		super();
		
		this.dataMsgListener = dataMsgListener;
		
		initServer();
		
		_Logger.info("CoAP server gateway created.");
	}

	// public methods
	
	/**
	 * Adds a resource to the CoAP server.
	 * 
	 * @param resourceType The resource name enum
	 * @param endName Optional end name for the resource
	 * @param resource The resource handler instance
	 */
	public void addResource(ResourceNameEnum resourceType, String endName, Resource resource)
	{
		if (resourceType != null && resource != null) {
			createAndAddResourceChain(resourceType, resource);
		}
	}
	
	/**
	 * Checks if the server has a resource with the given name.
	 * 
	 * @param name The resource name to check
	 * @return true if resource exists, false otherwise
	 */
	public boolean hasResource(String name)
	{
		if (this.coapServer != null && name != null) {
			return (this.coapServer.getRoot().getChild(name) != null);
		}
		
		return false;
	}
	
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
	
	/**
	 * Starts the CoAP server.
	 * 
	 * @return true if started successfully, false otherwise
	 */
	public boolean startServer()
	{
		try {
			if (this.coapServer != null) {
				this.coapServer.start();
				
				// Add message tracer for logging
				for (Endpoint ep : this.coapServer.getEndpoints()) {
					ep.addInterceptor(new MessageTracer());
				}
				
				_Logger.info("CoAP server started.");
				
				return true;
			} else {
				_Logger.warning("CoAP server START failed. Not yet initialized.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to start CoAP server.", e);
		}
		
		return false;
	}
	
	/**
	 * Stops the CoAP server.
	 * 
	 * @return true if stopped successfully, false otherwise
	 */
	public boolean stopServer()
	{
		try {
			if (this.coapServer != null) {
				this.coapServer.stop();
				
				_Logger.info("CoAP server stopped.");
				
				return true;
			} else {
				_Logger.warning("CoAP server STOP failed. Not yet initialized.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to stop CoAP server.", e);
		}
		
		return false;
	}
	
	// private methods
	
	/**
	 * Creates and adds a resource chain to the server.
	 * 
	 * @param resourceType The resource type enum
	 * @param resource The resource handler
	 */
	private void createAndAddResourceChain(ResourceNameEnum resourceType, Resource resource)
	{
		_Logger.info("Adding server resource handler chain: " + resourceType.getResourceName());
		
		List<String> resourceNames = resourceType.getResourceNameChain();
		Queue<String> queue = new ArrayBlockingQueue<>(resourceNames.size());
		
		queue.addAll(resourceNames);
		
		// Check if we have a parent resource
		Resource parentResource = this.coapServer.getRoot();
		
		// If no parent resource, add it in now (should be named "PIOT")
		if (parentResource == null) {
			parentResource = new CoapResource(queue.poll());
			this.coapServer.add(parentResource);
		}
		
		while (! queue.isEmpty()) {
			// Get the next resource name
			String   resourceName = queue.poll();
			Resource nextResource = parentResource.getChild(resourceName);
			
			if (nextResource == null) {
				// If this is the last resource in the chain, use the provided resource handler
				if (queue.isEmpty()) {
					nextResource = resource;
					nextResource.setName(resourceName);
				} else {
					// Otherwise create a new CoapResource as a placeholder
					nextResource = new CoapResource(resourceName);
				}
				
				parentResource.add(nextResource);
			}
			
			parentResource = nextResource;
		}
	}
	
	/**
	 * Initializes the CoAP server and default resources.
	 * 
	 * @param resources Optional resource types to initialize
	 */
	private void initServer(ResourceNameEnum ...resources)
	{
		try {
			this.coapServer = new CoapServer();
			
			initDefaultResources();
			
			_Logger.info("CoAP server initialized with default resources.");
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize CoAP server.", e);
		}
	}
	
	/**
	 * Initializes default resource handlers.
	 */
	private void initDefaultResources()
	{
		// Initialize GetActuatorCommandResourceHandler (observable)
		GetActuatorCommandResourceHandler getActuatorCmdResourceHandler =
			new GetActuatorCommandResourceHandler(
				ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE.getResourceType());
		
		if (this.dataMsgListener != null) {
			this.dataMsgListener.setActuatorDataListener(null, getActuatorCmdResourceHandler);
		}
		
		addResource(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, null, getActuatorCmdResourceHandler);
		
		// Initialize UpdateTelemetryResourceHandler
		UpdateTelemetryResourceHandler updateTelemetryResourceHandler =
			new UpdateTelemetryResourceHandler(
				ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE.getResourceType());
		
		updateTelemetryResourceHandler.setDataMessageListener(this.dataMsgListener);
		
		addResource(
			ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, null,	updateTelemetryResourceHandler);
		
		// Initialize UpdateSystemPerformanceResourceHandler
		UpdateSystemPerformanceResourceHandler updateSystemPerformanceResourceHandler =
			new UpdateSystemPerformanceResourceHandler(
				ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE.getResourceType());
		
		updateSystemPerformanceResourceHandler.setDataMessageListener(this.dataMsgListener);
		
		addResource(
			ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, null, updateSystemPerformanceResourceHandler);
	}
}