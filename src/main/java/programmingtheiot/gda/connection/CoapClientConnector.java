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

package programmingtheiot.gda.connection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.gda.connection.handlers.SensorDataObserverHandler;
import programmingtheiot.gda.connection.handlers.SystemPerformanceDataObserverHandler;

/**
 * Shell representation of class for student implementation.
 *
 */
public class CoapClientConnector implements IRequestResponseClient
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(CoapClientConnector.class.getName());
	
	// params
	
	private String protocol;
	private String host;
	private int port;
	private String serverAddr;
	private CoapClient clientConn;
	private IDataMessageListener dataMsgListener;
	private Map<String, CoapObserveRelation> observeRelations;
	
	// constructors
	
	/**
	 * Default.
	 * 
	 * All config data will be loaded from the config file.
	 */
	public CoapClientConnector()
	{
		ConfigUtil config = ConfigUtil.getInstance();
		this.host = config.getProperty(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.HOST_KEY, ConfigConst.DEFAULT_HOST);

		if (config.getBoolean(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.ENABLE_CRYPT_KEY)) {
			this.protocol = ConfigConst.DEFAULT_COAP_SECURE_PROTOCOL;
			this.port     = config.getInteger(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.SECURE_PORT_KEY, ConfigConst.DEFAULT_COAP_SECURE_PORT);
		} else {
			this.protocol = ConfigConst.DEFAULT_COAP_PROTOCOL;
			this.port     = config.getInteger(ConfigConst.COAP_GATEWAY_SERVICE, ConfigConst.PORT_KEY, ConfigConst.DEFAULT_COAP_PORT);
		}
		
		// NOTE: URL does not have a protocol handler for "coap",
		// so we need to construct the URL manually
		this.serverAddr = this.protocol + "://" + this.host + ":" + this.port;
		
		this.observeRelations = new HashMap<>();

		initClient();

		_Logger.info("Using URL for server conn: " + this.serverAddr);
	}
		
	/**
	 * Constructor.
	 * 
	 * @param host
	 * @param isSecure
	 * @param enableConfirmedMsgs
	 */
	public CoapClientConnector(String host, boolean isSecure, boolean enableConfirmedMsgs)
	{
	}
	
	
	// public methods
	
	@Override
	public boolean sendDiscoveryRequest(int timeout)
	{
		try {
			_Logger.info("Issuing discover...");
			
			Set<WebLink> wlSet = this.clientConn.discover();

			if (wlSet != null) {
				for (WebLink wl : wlSet) {
					_Logger.info(" --> URI: " + wl.getURI() + ". Attributes: " + wl.getAttributes());
				}
				
				return true;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to execute discovery request", e);
		}
		
		return false;
	}

	@Override
	public boolean sendDeleteRequest(ResourceNameEnum resource, String name, boolean enableCON, int timeout)
	{
		try {
			CoapResponse response = null;

			if (enableCON) {
				this.clientConn.useCONs();
			} else {
				this.clientConn.useNONs();
			}

			this.clientConn.setURI(this.serverAddr + "/" + resource.getResourceName());
			response = this.clientConn.delete();

			if (response != null) {
				_Logger.info("Handling DELETE. Response: " + response.isSuccess() + " - " + response.getOptions() + " - " +
					response.getCode() + " - " + response.getResponseText());
				
				if (this.dataMsgListener != null) {
					// TODO: implement this
				}
				
				return true;
			} else {
				_Logger.warning("Handling DELETE. No response received.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to execute DELETE request", e);
		}

		return false;
	}

	@Override
	public boolean sendGetRequest(ResourceNameEnum resource, String name, boolean enableCON, int timeout)
	{
		try {
			CoapResponse response = null;

			if (enableCON) {
				this.clientConn.useCONs();
			} else {
				this.clientConn.useNONs();
			}

			this.clientConn.setURI(this.serverAddr + "/" + resource.getResourceName());
			response = this.clientConn.get();

			if (response != null) {
				_Logger.info("Handling GET. Response: " + response.isSuccess() + " - " + response.getOptions() + " - " +
					response.getCode() + " - " + response.getResponseText());
				
				if (this.dataMsgListener != null) {
					// TODO: implement this
				}
				
				return true;
			} else {
				_Logger.warning("Handling GET. No response received.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to execute GET request", e);
		}

		return false;
	}

	@Override
	public boolean sendPostRequest(ResourceNameEnum resource, String name, boolean enableCON, String payload, int timeout)
	{
		try {
			CoapResponse response = null;

			if (enableCON) {
				this.clientConn.useCONs();
			} else {
				this.clientConn.useNONs();
			}

			this.clientConn.setURI(this.serverAddr + "/" + resource.getResourceName());
			response = this.clientConn.post(payload, MediaTypeRegistry.TEXT_PLAIN);

			if (response != null) {
				_Logger.info("Handling POST. Response: " + response.isSuccess() + " - " + response.getOptions() + " - " +
					response.getCode() + " - " + response.getResponseText());
				
				if (this.dataMsgListener != null) {
					// TODO: implement this
				}
				
				return true;
			} else {
				_Logger.warning("Handling POST. No response received.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to execute POST request", e);
		}

		return false;
	}

	@Override
	public boolean sendPutRequest(ResourceNameEnum resource, String name, boolean enableCON, String payload, int timeout)
	{
		try {
			CoapResponse response = null;

			if (enableCON) {
				this.clientConn.useCONs();
			} else {
				this.clientConn.useNONs();
			}

			this.clientConn.setURI(this.serverAddr + "/" + resource.getResourceName());
			response = this.clientConn.put(payload, MediaTypeRegistry.TEXT_PLAIN);

			if (response != null) {
				_Logger.info("Handling PUT. Response: " + response.isSuccess() + " - " + response.getOptions() + " - " +
					response.getCode() + " - " + response.getResponseText());
				
				if (this.dataMsgListener != null) {
					// TODO: implement this
				}
				
				return true;
			} else {
				_Logger.warning("Handling PUT. No response received.");
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to execute PUT request", e);
		}

		return false;
	}

	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
			return true;
		}
		
		return false;
	}

	public void clearEndpointPath()
	{
	}
	
	public void setEndpointPath(ResourceNameEnum resource)
	{
	}
	
	@Override
	public boolean startObserver(ResourceNameEnum resource, String name, int ttl)
	{
		String uriPath = createUriPath(resource, name);
		
		_Logger.info("Observing resource [START]: " + uriPath);
		
		this.clientConn.setURI(uriPath);
		
		// Check the resource type and create appropriate handler
		SensorDataObserverHandler handler = new SensorDataObserverHandler();
		handler.setDataMessageListener(this.dataMsgListener);
		
		CoapObserveRelation cor = this.clientConn.observe(handler);
		
		// Store the relation for later cancellation
		this.observeRelations.put(uriPath, cor);
		
		return (! cor.isCanceled());
	}

	@Override
	public boolean stopObserver(ResourceNameEnum resource, String name, int timeout)
	{
		String uriPath = createUriPath(resource, name);
		
		_Logger.info("Observing resource [STOP]: " + uriPath);
		
		CoapObserveRelation cor = this.observeRelations.get(uriPath);
		
		if (cor != null) {
			cor.proactiveCancel();
			this.observeRelations.remove(uriPath);
			
			return true;
		}
		
		return false;
	}

	
	// private methods
	
	private String createUriPath(ResourceNameEnum resource, String name)
	{
		String uriPath = this.serverAddr;
		
		if (resource != null) {
			uriPath = uriPath + "/" + resource.getResourceName();
		}
		
		if (name != null && name.trim().length() > 0) {
			uriPath = uriPath + "/" + name;
		}
		
		return uriPath;
	}
	
	private void initClient()
	{
		try {
			// Create the CoAP configuration
			//
			// NOTE 1: You can create a custom configuration as well by setting
			// NOTE 2: These settings are for UDP only. See RFC 8323 for more about CoAP/TCP.
			// (https://datatracker.ietf.org/doc/html/rfc8323)
			Configuration config = new Configuration(
				CoapConfig.DEFINITIONS, 
				UdpConfig.DEFINITIONS
			);

			Configuration.setStandard(config);
			_Logger.info("Initialized Californium configuration with standard definitions");
			
			// Create the CoAP client
			this.clientConn = new CoapClient(this.serverAddr);
			
			_Logger.info("Created client connection to server / resource: " + this.serverAddr);

		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to connect to server: " + this.serverAddr, e);
		}
	}
	
}