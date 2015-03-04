package service;

import service.CallMySql;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.StringBufferInputStream;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.mina.core.session.IoSession;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import cache.Cache;
import cache.VehiclePluginRecord;
import common.GlobalVariables;
import dao.AppConfigDao;
import dao.AppConfigDaoImpl;
import dao.ApplicationDao;
import dao.ApplicationDaoImpl;
import dao.DBConnection;
import dao.DatabasePluginDao;
import dao.DatabasePluginDaoImpl;
import dao.PluginConfigDaoImpl;
import dao.VehicleConfigDao;
import dao.VehicleConfigDaoImpl;
import dao.VehicleDao;
import dao.VehicleDaoImpl;
import dao.VehiclePluginDao;
import dao.VehiclePluginDaoImpl;
import messages.InstallPacket;
import messages.InstallPacketData;
import messages.LinkContextEntry;
import messages.RestorePacket;
import messages.UninstallPacket;
import messages.UninstallPacketData;
import mina.ServerHandler;
import model.AppConfig;
import model.Application;
import model.DatabasePlugin;
import model.Ecu;
import model.Link;
import model.PluginConfig;
import model.PluginLinkConfig;
import model.PluginPortConfig;
import model.Port;
import model.Vehicle;
import model.VehicleConfig;
import model.VehiclePlugin;
import service.exception.PluginWebServicesException;
import utils.CompressUtils;
import utils.SuiteGen;

import java.sql.*;


@WebService(endpointInterface = "service.PluginWebServices")
public class PluginWebServicesImpl implements PluginWebServices {
	private ApplicationDao applicationDao;
	private VehiclePluginDao vehiclePluginDao;
	private VehicleDao vehicleDao;
	private VehicleConfigDao vehicleConfigDao;
	private DatabasePluginDao databasePluginDao;
	private AppConfigDao appConfigDao;
//	private PluginConfigDaoImpl pluginConfigDao;
	private SuiteGen suiteGen = new SuiteGen("/lhome/sse/squawk");
	
	private DBConnection db = null;
	private ServerHandler handler = null;
	
    private Connection dbLite = null;
	private Statement stat = null;
	
	public PluginWebServicesImpl(ServerHandler handler) {
		this.handler = handler;
		
		db = new DBConnection();
		
		vehicleDao = new VehicleDaoImpl(db);
		vehicleConfigDao = new VehicleConfigDaoImpl(db);
		vehiclePluginDao = new VehiclePluginDaoImpl(db);
		appConfigDao = new AppConfigDaoImpl(db);
		applicationDao = new ApplicationDaoImpl(db);
		databasePluginDao = new DatabasePluginDaoImpl(db);
//		pluginConfigDao = new PluginConfigDaoImpl(db);
	}
	
	@Override
	public void insertPluginInDb(String location, String name) 
			throws PluginWebServicesException {
	    File configFile = null;
		
	    try {
		File loc = new File(location);
		location = loc.getCanonicalPath();
		
		JarFile jar = new JarFile(new File(location + File.separator + name + ".jar"));
		Manifest mf = jar.getManifest();
		if (mf != null) {		
		    Attributes attributes = mf.getMainAttributes();
				
		    String publisher = attributes.getValue("Built-By");
		    if (publisher == null)
			publisher = "unknown";
		    String version = attributes.getValue("Manifest-Version");
		    if (version == null)
			version = "1.0";
		    String brand = attributes.getValue("Vehicle-Brand");
		    if (brand == null)
			brand = "SICS";
		    String vehicleName = attributes.getValue("Vehicle-Name");
		    if (vehicleName == null)
			vehicleName = "MOPED";
		    String ecuRef = attributes.getValue("Ecu");
		    if (ecuRef == null)
			ecuRef = "0";
		    String configFileName = attributes.getValue("Pirte-Config");
		    if (configFileName == null)
			configFileName = name + ".xml";
		    //TODO: UGLY HACK (only allows one .class file)
		    String fullClassName = "";

		    for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
			JarEntry entry = entries.nextElement();
			String fileName = entry.getName();
			if (fileName.endsWith(".class")) {
			    fullClassName = fileName.substring(0, fileName.length() - 6);
			} else if (fileName.equals(configFileName)) {
			    configFile = File.createTempFile("tempxml.xml", null);
			    BufferedReader reader = new BufferedReader(new InputStreamReader(
											     jar.getInputStream(entry)));
			    BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
			    String line;
			    while ((line = reader.readLine()) != null) {
				writer.write(line);
			    }
			    reader.close();
			    writer.close();
			}
		    }
				
				
		    //Application application = new Application(name, publisher, version);
		    int appId;
		    // appId = applicationDao.saveApplication(application);

		    String q1 = "select applicationId from Application where applicationName = '" + name + "' and publisher = '" + publisher + "' and version = '" + version + "'";
		    String c1 = CallMySql.getOne(q1);
		    if (c1 == "none") {
			
			String q2 = "insert into Application (applicationName,publisher,version,hasNewVersion) values ('" + name + "','" + publisher + "','" + version + "',0)";
			int rows = CallMySql.update(q2);

			String q3 = "select applicationId from Application where applicationName = '" + name + "' and publisher = '" + publisher + "' and version = '" + version + "'";
			String c3 = CallMySql.getOne(q3);
			appId = Integer.parseInt(c3);
		    } else {
			// should it be allowed to insert a new copy into an
			// existing version?
			// then we should also delete the old one
			// for the time being we make an unwarranted assumption,
			// namely that the old entries don't hurt (that the new ones
			// are the same)
			appId = Integer.parseInt(c1);
		    }


		    System.out.println("new appId " + appId);
		    int appConfigId = appConfigDao.saveAppConfig
			(
			 new AppConfig(appId, vehicleName, brand)
			 );
		    DatabasePlugin dbPlugin = new DatabasePlugin
			(
			 name, name + ".zip", fullClassName, 
			 "", Integer.parseInt(ecuRef), 
			 location + File.separator + name, location
			 );
		    dbPlugin.setApplication(applicationDao.getApplication(appId));
		    databasePluginDao.saveDatabasePlugin(dbPlugin);
		    //TODO: Why + .suite???
		    PluginConfig pluginConfig = new PluginConfig(name + ".suite", Integer.parseInt(ecuRef));
		    pluginConfig.setAppConfig(appConfigDao.getAppConfig(appConfigId));
		    //TODO: Inconsequence
		    appConfigDao.savePluginConfig(pluginConfig);
				
		    String q3 = "select * from PluginConfig where ecuId = " +
			Integer.parseInt(ecuRef) + " and " +
			"name = " + "'" + name + ".suite7" +"'" + " and " +
			"appConfig_id = " + appConfigId;
		    String c3 = CallMySql.getOne(q3);
		    System.out.println(c3);

		    if (c3 == "none") {
			String q31 = "insert into PluginConfig (ecuId,name,appConfig_id) values (" +
			    Integer.parseInt(ecuRef) + "," +
			    "'" + name + ".suite7" +"'" + "," +
			    appConfigId + ")";
			int x3 = CallMySql.update(q31);
			System.out.println("updated rows " + x3);
		    }

		    //TODO: Refactor
		    // Xml-parsing
		    Document doc = DocumentBuilderFactory.newInstance().
			newDocumentBuilder().parse(configFile);
		    doc.getDocumentElement().normalize();
				
		    NodeList ports = doc.getElementsByTagName("port");
		    for (int i = 0; i < ports.getLength(); i++) {
			Element port = (Element)ports.item(i);
			String portName = port.getElementsByTagName("name").item(0).getTextContent();
					
			PluginPortConfig portConfig = new PluginPortConfig(portName);
			portConfig.setPluginConfig(appConfigDao.getPluginConfig(pluginConfig));
			appConfigDao.savePluginPortConfig(portConfig);
		    }
				
		    NodeList links = doc.getElementsByTagName("link");
		    for (int i = 0; i < ports.getLength(); i++) {
			Element link = (Element)links.item(i);
			String linkSource = link.getElementsByTagName("from").item(0).getTextContent();
			String linkTarget = link.getElementsByTagName("to").item(0).getTextContent();

			int connectionType = GlobalVariables.PPORT2PPORT;
			if (linkSource.matches("(\\d+)")) {
			    connectionType = GlobalVariables.VPORT2PORT;
			}
			if (linkTarget.matches("(\\d+)")) {
			    connectionType = GlobalVariables.PPORT2VPORT;
			}
					
			PluginLinkConfig linkConfig = new PluginLinkConfig(linkSource, linkTarget, connectionType);
			linkConfig.setPluginConfig(appConfigDao.getPluginConfig(pluginConfig));
			appConfigDao.savePluginLinkConfig(linkConfig);
		    }
		}
		else {
		    System.out.println("MF is NULL");
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    } catch (SAXException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (ParserConfigurationException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (FactoryConfigurationError e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } 
		
	    System.out.println("insertPluginInDb done");
	}
	

	@Override
	public boolean get_ack_status(String vin, int appId)
	    throws PluginWebServicesException {

	    String q1 =
		"select * FROM Application a " +
		"WHERE a.applicationId = " + appId;
	    String x = CallMySql.getOne(q1);

	    if (x == "none") {
		return false;
	    }

	    String q2 =
		"select applicationName from Application " + 
		"where applicationId = " + appId;
	    String name = CallMySql.getOne(q2);

	    if (handler.existsAckMessage(vin + "_" + name))
		return true;
		
	    return false;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public String install(String vin, int appID, String jvm) 
			throws PluginWebServicesException {
	    System.out.println("vin in install(): " + vin);
	    System.out.println("appID in install(): " + appID);
		
	    // Fetch the connection session between Server and Vehicle
	    IoSession session = ServerHandler.getSession(vin);
	    if (session == null) {
		// If null, there is no connection between the server and the vehicle
		System.out.println("IoSession is NULL");
		return "false";
	    } else {
		System.out.println("IoSession.address: " + session.getLocalAddress());
			
		// Achieve contexts
		// key: portName(String), value:
		// portId(Integer)>
		HashMap<String, Integer> portInitialContext = new HashMap<String, Integer>();
		// HashMap<String, ArrayList<LinkingContextEntry>> linkingContexts =
		// new HashMap<String, ArrayList<LinkingContextEntry>>();
		HashMap<String, ArrayList<LinkContextEntry>> linkingContexts = new HashMap<String, ArrayList<LinkContextEntry>>();

		// Create an array list for cache
		ArrayList<VehiclePluginRecord> installCachePlugins = new ArrayList<VehiclePluginRecord>();

		System.out.println("Fetching vehicle, vehicleDao: " + vehicleDao);
			

		String q1 =
		    "select vehicleConfigId from Vehicle where VIN = '" + vin
		    + "'";
		String c = CallMySql.getOne(q1);
		System.out.println("vehicle " + c);

		String q2 =
		    "select name,brand from VehicleConfig where id = " + c;
		String [] c2 = CallMySql.getOneSet(q2);
		if (c2 != null) {
		    System.out.println("vehicleconf name " + c2[0]);
		    System.out.println("vehicleconf brand " + c2[1]);
		}

		Vehicle vehicle = vehicleDao.getVehicle(vin);
		System.out.println("Vehicle found: " + vehicle);

		// VehicleConfig
		int vehicleConfigId = vehicle.getVehicleConfigId();
		VehicleConfig vehicleConfig = vehicleConfigDao
		    .getVehicleConfig(vehicleConfigId);
		System.out.println("VehicleConfig found: " + vehicleConfig);
			
		// AppConfig
		String vehicleName = vehicleConfig.getName();
		String brand = vehicleConfig.getBrand();
		System.out.println("Found vehicle: " + vehicleName + " of brand: " + brand + "... (next step not implemented yet)");
		AppConfig appConfig = appConfigDao.getAppConfig(appID, vehicleName, brand);
		System.out.println("AppConfig found, id: " + appConfig.getId());
			
		String q3 = "select id from AppConfig where appId = " + appID
		    + " and brand = '" + brand + "' and vehicleName = '" +
		    vehicleName + "'";
		String c3 = CallMySql.getOne(q3);
		System.out.println("appconfig id " + c3);

		String q4 = "select id from PluginConfig where appConfig_id = " + c3;
		String c4 = CallMySql.getOne(q4);
		System.out.println("pluginconfig id " + c4);

		// PluginConfig
		//TODO: Move it to a warmer place
		if (appConfig != null) {
		    List<PluginConfig> pluginConfigs = (List<PluginConfig>)db.getAllResults(
											    "FROM PluginConfig pc WHERE pc.appConfig = " + appConfig.getId());
		    for (PluginConfig pluginConfig : pluginConfigs) {
			System.out.println("pluginConfig.id: " + pluginConfig.getId());
					
			List<PluginPortConfig> pluginPortConfigs = db.getAllResults(
										    "FROM PluginPortConfig ppc WHERE ppc.pluginConfig = " + pluginConfig.getId());
			for (PluginPortConfig pluginPortConfig : pluginPortConfigs) {
			    int pluginPortId = pluginPortConfig.getId();
			    String pluginPortName = pluginPortConfig.getName();
			    portInitialContext.put(pluginPortName, pluginPortId);
			}
		    }
				
		    //TEMP_DEBUG
		    System.out.println("PORT INITIAL CONTEXTS: ");
		    for (String ctxt : portInitialContext.keySet()) {
			System.out.println("PIC: <" + ctxt + ", " + portInitialContext.get(ctxt) + ">");
		    }
					
		    for (PluginConfig pluginConfig : pluginConfigs) {
			// Plugin Link Config
			String pluginName = pluginConfig.getName();
			// Initiate LinkingContext
			ArrayList<LinkContextEntry> linkingContext = new ArrayList<LinkContextEntry>();
				
			List<PluginLinkConfig> pluginLinkConfigs = db.getAllResults(
										    "FROM PluginLinkConfig plc WHERE plc.pluginConfig = " + pluginConfig.getId());
			for (PluginLinkConfig pluginLinkConfig : pluginLinkConfigs) {
			    String from = pluginLinkConfig.getFromStr();
			    String to = pluginLinkConfig.getToStr();
			    String remote = pluginLinkConfig.getRemote();

			    int fromPortId = 0;
			    int toPortId = 0;
			    int remoteId = 0;

			    Scanner scanner = new Scanner(remote);
			    boolean remoteTag = scanner.hasNextInt();

			    if (remoteTag) {
				remoteId = scanner.nextInt();
				switch (remoteId) {
				case GlobalVariables.PPORT2PPORT:
				    fromPortId = portInitialContext.get(from);
				    toPortId = portInitialContext.get(to);
				    break;
				case GlobalVariables.PPORT2VPORT:
				    fromPortId = portInitialContext.get(from);
				    toPortId = Integer.parseInt(to);
				    break;
				case GlobalVariables.VPORT2PORT:
				    fromPortId = Integer.parseInt(from);
				    toPortId = portInitialContext.get(to);
				    break;
				default:
				    System.out.println("Error: Wrong link type in GlobalVariables");
				    System.exit(-1);
				}
			    } else {
				// Plug-In -> VRPort
				// remote represents the name of remote port
				remoteId = portInitialContext.get(remote);
				fromPortId = portInitialContext.get(from);
				toPortId = Integer.parseInt(to);
			    }
						
			    scanner.close();

			    LinkContextEntry entry = new LinkContextEntry(fromPortId,
									  toPortId, remoteId);
			    linkingContext.add(entry);
			}
				
			linkingContexts.put(pluginName, linkingContext);
					
			//TEMP_DEBUG
			System.out.println("PORT LINKING CONTEXTS (" + pluginName + "): ");
			for (Iterator<LinkContextEntry> ctxtIter = linkingContext.iterator(); ctxtIter.hasNext(); ) {
			    LinkContextEntry ctxt = ctxtIter.next();
			    System.out.println("PLC: <" + ctxt.getFromPortId() + ", " + ctxt.getToPortId() + "> via " + ctxt.getRemotePortId());
			}
		    }
		}

		// Achieve jars
		ArrayList<InstallPacketData> installPackageDataList = new ArrayList<InstallPacketData>();

		// Fetch application data from DB
		Application application = applicationDao.getApplication(appID);
		System.out.println("Found application: " + application.getApplicationName());
			
		String q10 =
		    "select applicationName from Application where applicationId = " + appID;
		String c10 = CallMySql.getOne(q10);
		System.out.println("application name " + c10);

		// Fetch PlugIns from DB
		// HashMap<String, Byte> contexts = new HashMap<String, Byte>();
		@SuppressWarnings("unchecked")
		    List<DatabasePlugin> plugins = (List<DatabasePlugin>)db.getAllResults(
											  "FROM DatabasePlugin dp WHERE dp.application = " + application.getApplicationId());
		//			Set<DatabasePlugin> plugins = application.getDatabasePlugins();

		String q11 =
		    "select name from DatabasePlugin where application_applicationId = " + appID;
		String c11 = CallMySql.getOne(q11);
		System.out.println("plugin name " + c11);

		// We must loop over the solutions to q11


		System.out.println("Found plugins, size: " + plugins.size());
		for (DatabasePlugin plugin : plugins) {
		    String pluginName = plugin.getName();
		    System.out.println("Found plugin with name: " + pluginName);
				
		    String q12 =
			"select reference,location,fullClassName " +
			"from DatabasePlugin where application_applicationId = " + appID;
		    String [] c12 = CallMySql.getOneSet(q12);
		    System.out.println(c12[0]);
		    System.out.println(c12[1]);
		    System.out.println(c12[2]);

		    int remoteEcuId = plugin.getReference();
		    int sendingPortId = vehicleConfigDao.getSendingPortId
			(
			 vehicleConfigId, remoteEcuId
			 );
		    int callbackPortId = vehicleConfigDao.getCallbackPortId
			(
			 vehicleConfigId, remoteEcuId
			 );
		    System.out.println("sendingPortId " + sendingPortId);
		    System.out.println("callbackPortId " + callbackPortId);

		    String executablePluginName = "plugin://"
			+ plugin.getFullClassName() + "/" + pluginName;
		    String pluginSuiteName = pluginName + ".suite"; //TODO: Come on...
		    // Find PlugIn location. For instance,
		    // some_dir/uploaded/app_name/version/kdkdks.zip
		    String location = /*
				       * GlobalVariables.APPS_DIR +
				       */plugin.getLocation();
				
		    String fileType = ".zip";
		    if (jvm.equals("Squawk")) {
			fileType = ".suite";
			location += File.separator + pluginName;
		    }
				
		    location += fileType;
		    pluginName += fileType;
		    executablePluginName += fileType;
				
		    File file = new File(location);
		    byte[] fileBytes;
		    try {
			// ArrayList<LinkingContextEntry> linkingContext =
			// linkingContexts
			// .get(pluginName);
			ArrayList<LinkContextEntry> linkingContext = (ArrayList<LinkContextEntry>) linkingContexts
			    .get(pluginSuiteName);
			fileBytes = readBytesFromFile(file);
			InstallPacketData installPacketData =
			    new InstallPacketData
			    (
			     appID, pluginName/* +".zip" */, sendingPortId,
			     callbackPortId, remoteEcuId, portInitialContext,
			     linkingContext, executablePluginName, fileBytes
			     );
			installPackageDataList.add(installPacketData);

			// Store it temporarily to cache and will be used after the
			// arrival of acknowledge messages
			VehiclePluginRecord record =
			    new VehiclePluginRecord
			    (
			     pluginName, remoteEcuId, sendingPortId,
			     callbackPortId, portInitialContext, linkingContext,
			     location, executablePluginName
			     );

			installCachePlugins.add(record);
					
			System.out.println("READY FOR INSTALLATION WRITING!!!!!!!!!!!!!!!!!!!!");
					
			Cache.getCache().addInstallCache(vin, appID, installCachePlugins);
			InstallPacket installPacket = new InstallPacket(vin,
									installPackageDataList);
			session.write(installPacket);
					
			System.out.println("SUCCESSFULLY INSTALLED SOME STUFF!!!!!!!!!!!!!!!!!!!!");

		    } catch (IOException e) {
			e.printStackTrace();
		    }
		}

		return "true";
	    }
	}

	public boolean uninstall(String vin, int appID)
			throws PluginWebServicesException {
	    IoSession session = ServerHandler.getSession(vin);
	    if (session == null) {
		// If null, response user about the disconnection between Sever and
		// Vehicle
		return false;
	    } else {
		// Fetch un_installation PlugIns
		ArrayList<UninstallPacketData> uninstallPackageDataList = new ArrayList<UninstallPacketData>();

		// Save pluginname into array list cache for uninstallation
		ArrayList<String> uninstallCacheName = new ArrayList<String>();

		@SuppressWarnings("unchecked")
		    List<VehiclePlugin> vehiclePlugins = (List<VehiclePlugin>)db.getAllResults("FROM VehiclePlugin vp WHERE vp.appId = " + appID + " AND vp.vin = '" + vin + "'");

		String q1 = "select name,sendingPortId,callbackPortId,ecuId from VehiclePlugin where appId = " + appID + " and vin = '" + vin + "'";
		String [] c1 = CallMySql.getOneSet(q1);

		System.out.println(c1[0]);
		System.out.println(c1[1]);
		System.out.println(c1[2]);
		System.out.println(c1[3]);

		// We should fetch all rows, but we don't expect there to
		// be more than one.

		for (VehiclePlugin vehiclePlugin : vehiclePlugins) {
		    String pluginName = vehiclePlugin.getName();
		    int sendingPortId = vehiclePlugin.getSendingPortId();
		    int callbackPortId = vehiclePlugin.getCallbackPortId();
		    int reference = vehiclePlugin.getEcuId();

		    UninstallPacketData uninstallPackageData = new UninstallPacketData(
										       sendingPortId, callbackPortId, reference, pluginName);
		    uninstallPackageDataList.add(uninstallPackageData);

		    uninstallCacheName.add(pluginName);
		}

		Cache.getCache().addUninstallCache(vin, appID, uninstallCacheName);

		UninstallPacket uninstallPackage = new UninstallPacket(vin,
								       uninstallPackageDataList);
		session.write(uninstallPackage);
		return true;
	    }
	}

	public boolean upgrade(String vin, int oldAppID)
			throws PluginWebServicesException {
		IoSession session = ServerHandler.getSession(vin);

		// Do a transaction around q1 and q2.

		String q1 = "select applicationName from Application where applicationId = " + oldAppID;
		String c1 = CallMySql.getOne(q1);

		System.out.println("upgrade " + c1);

		if (c1 == "none") {
		    System.out.println("no such appid");
		    return false;
		}

		String q2 = "select max(applicationId) from Application where applicationName = '" + c1 + "'";
		String c2 = CallMySql.getOne(q2);

		System.out.println("old " + oldAppID + " new " + c2);
		int newAppId = Integer.parseInt(c2);
		// upgrade only if this is newer?
		// use date or version instead?

		if (oldAppID == newAppId)
		    return false;

		if (session == null) {
			// If null, response user about the disconnection between Sever and
			// Vehicle
		    System.out.println("upgrade: no session");
			return false;
		} else {
			uninstall(vin, oldAppID);
			try {
				Thread.sleep(2000);
				if (newAppId > -1) {
					install(vin, newAppId, "Squawk"); //TODO: This should also be implemented for JDK (all the way to the upgrade button in the php-interface)
					return true;
				}
				else 
					return false;
			} catch (InterruptedException e) {
				e.printStackTrace();
				return false;
			}
		}

	}

	private byte[] readBytesFromFile(File file) throws IOException {
		InputStream is = new FileInputStream(file);
		// Get the size of the file
		long length = file.length();
		// You cannot create an array using a long type.
		// It needs to be an integer type.
		// Before converting to an integer type, check
		// to ensure that file is not larger than Integer.MAX_VALUE.
		if (length > Integer.MAX_VALUE) {
			is.close();
			throw new IOException("Could not completely read file "
					+ file.getName() + " as it is too long (" + length
					+ " bytes, max supported " + Integer.MAX_VALUE + ")");
		}

		// Create the byte array to hold the data
		byte[] bytes = new byte[(int) length];

		// Read in the bytes
		int offset = 0;
		int numRead = 0;
		while (offset < bytes.length
				&& (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
			offset += numRead;
		}

		// Ensure all the bytes have been read in
		if (offset < bytes.length) {
			is.close();
			throw new IOException("Could not completely read file "
					+ file.getName());
		}

		// Close the input stream and return bytes
		is.close();
		return bytes;
	}

	@WebMethod
	public boolean restoreEcu(String vin, int ecuReference)
			throws PluginWebServicesException {
	    IoSession session = ServerHandler.getSession(vin);
	    if (session == null) {
		// If null, response user about the disconnection between Sever and
		// Vehicle
		return false;
	    } else {

		ArrayList<InstallPacketData> installPackageDataList = new ArrayList<InstallPacketData>();
		String q1 = "select appId,name,sendingPortId,callbackPortId,ecuId,executablePluginName,portInitialContext,portLinkingContext,location from VehiclePlugin where vin = '" +
		    vin + "' and ecuId = " + ecuReference;
		String [] c1 = CallMySql.getOneSet(q1);

		if (c1 == null) {
		    System.out.println("nothing in db");
		    return false;
		}

		String appId = c1[0];
		String pluginName = c1[1];
		String sendingPortId = c1[2];
		String callbackPortId = c1[3];
		String reference = c1[4];
		String executablePluginName = c1[5];
		String portInitialContext_blob = c1[6];
		String portLinkingContext_blob = c1[7];
		String location = c1[8];

		System.out.println("blob " + portInitialContext_blob);
		System.out.println("blob " + portLinkingContext_blob);

		HashMap<String, Integer> portInitialContext;
		ArrayList<LinkContextEntry> portLinkingContext;

		if (portInitialContext_blob == null) {
		    portInitialContext = null;
		} else {
		    try {
			StringBufferInputStream fileIn = new StringBufferInputStream
			    (portInitialContext_blob);

			ObjectInputStream in = new ObjectInputStream(fileIn);
			portInitialContext =
			    (HashMap<String, Integer>)
			    in.readObject();
			in.close();
			fileIn.close();
		    } catch(IOException e) {
			System.out.println("reading from object");
			e.printStackTrace();
			throw new PluginWebServicesException();
		    } catch(ClassNotFoundException e) {
			System.out.println("class not found");
			e.printStackTrace();
			throw new PluginWebServicesException();
		    }
		}

		if (portLinkingContext_blob == null) {
		    portLinkingContext = null;
		} else {
		    try {
			StringBufferInputStream fileIn = new StringBufferInputStream
			    (portLinkingContext_blob);

			ObjectInputStream in = new ObjectInputStream(fileIn);
			portLinkingContext =
			    (ArrayList<LinkContextEntry>)
			    in.readObject();
			in.close();
			fileIn.close();
		    } catch(IOException e) {
			System.out.println("reading from object");
			e.printStackTrace();
			throw new PluginWebServicesException();
		    } catch(ClassNotFoundException e) {
			System.out.println("class not found");
			e.printStackTrace();
			throw new PluginWebServicesException();
		    }
		}

		//HashMap<String, Integer> portInitialContext;
		//ArrayList<LinkContextEntry> portLinkingContext;

		System.out.println("restoreEcu 1");

		File file = new File(location);
		byte[] fileBytes;
		try {
		    fileBytes = readBytesFromFile(file);
		    InstallPacketData installPackageData =
			new InstallPacketData
			(
			 Integer.parseInt(appId),
			 pluginName,
			 Integer.parseInt(sendingPortId),
			 Integer.parseInt(callbackPortId),
			 Integer.parseInt(reference),
			 portInitialContext,
			 portLinkingContext,
			 executablePluginName,
			 fileBytes
			 );
		    installPackageDataList.add(installPackageData);
		} catch (IOException e) {
		    System.out
			.println("Error! Fail to read PlugIn file from Server.");
		    return false;
		}

		System.out.println("restoreEcu 2");
		RestorePacket restorePackage = new RestorePacket
		    (vin,
		     installPackageDataList);
		session.write(restorePackage);
		return true;
	    }

	}

	@Override
	public String parseVehicleConfiguration(String path) 
			throws PluginWebServicesException {
	    System.out.println("In parseVehicleConfiguration");
	    System.out.println("pwd: " + java.nio.file.Paths.get(".").toAbsolutePath().normalize().toString());
	    System.out.println("config path: " + path);

	    // key: port ID, value: ECU ID
	    HashMap<Integer, Integer> portId2EcuId = new HashMap<Integer, Integer>();

	    DocumentBuilderFactory domfac = DocumentBuilderFactory.newInstance();
	    try {
		DocumentBuilder dombuilder = domfac.newDocumentBuilder();
		InputStream is = new FileInputStream(path);
		Document doc = dombuilder.parse(is);
		// vehicle
		Element root = doc.getDocumentElement();
		System.out.println("Start parsing XML");

		// name of vehicle
		Element vehicleName = (Element) root.getElementsByTagName("name")
		    .item(0);
		String vehicleNameStr = vehicleName.getTextContent();

		// brand of vehicle
		Element vehicleBrand = (Element) root.getElementsByTagName("brand")
		    .item(0);
		String vehicleBrandStr = vehicleBrand.getTextContent();
		if (vehicleBrandStr == null) {
		    vehicleBrandStr = "";
		}

		int rows;
		String q1 = "select id from VehicleConfig where brand = '" +
		    vehicleBrandStr + "' and name = '" + vehicleNameStr + "'";
		String c1 = CallMySql.getOne(q1);

		if (c1 != "none") {
		    String q3 = "update VehicleConfig set name = '_deleted_' where id = " + c1;
		    rows = CallMySql.update(q3);
		}

		/*
 delete c.id from Ecu a,VehicleConfig b,Port c where a.vehicleConfig_id = b.id and b.name='_deleted_' and c.ecu_id=a.id;

delete a from Ecu a,VehicleConfig b where a.vehicleConfig_id = b.id and b.name='_deleted_';

delete b from VehicleConfig b where name='_deleted_';
		 */


		if (false) {
		    String q3 = "update Link set vehicleConfig_id = 0 where vehicleConfig_id = " + c1;
		    rows = CallMySql.update(q3);

		}

		String q2 = "insert into VehicleConfig (brand,name) " +
		    "values ('" + vehicleBrandStr + "','" + vehicleNameStr + "')";
		rows = CallMySql.update(q2);

		String q3 = "select id from VehicleConfig where brand = '" +
		    vehicleBrandStr + "' and name = '" + vehicleNameStr + "'";
		String c3 = CallMySql.getOne(q3);

		// VIN
		Element vinElement = (Element) root.getElementsByTagName("vin").item(0);
		if(vinElement == null) {
		    System.out.println("There is no VIN element in vehicle configuration file");
		    System.exit(-1);
		}
		String vinStr = vinElement.getTextContent();
			
		//			Vehicle vehicle = new Vehicle();
		//			vehicle.setName(vehicleNameStr);
		//			vehicle.setVIN(vinStr);
		//			vehicle.setVehicleConfigId(vehicleConfig.getId());
		//			vehicleDao.saveVehicle(vehicle);
			
		// ecus
		Element ecusElement = (Element) root.getElementsByTagName("ecus")
		    .item(0);
		if (ecusElement == null) {
		    System.out.println
			("There is no ecus element in vehicle configuration file");
		    System.exit(-1);
		}

		NodeList ecuList = ecusElement.getElementsByTagName("ecu");

		for (int i = 0; i < ecuList.getLength(); i++) {
		    // ecu
		    System.out.println("<ecu>");
		    Element ecuElement = (Element) ecuList.item(i);
		    if (ecuElement == null) {
			System.out.println
			    ("There is no ecu element in vehicle " +
			     "configuration file");
			System.exit(-1);
		    }
		    Element idElement = (Element)
			ecuElement.getElementsByTagName("id").item(0);
		    if (idElement == null) {
			System.out.println
			    ("There is no id element " +
			     "in ecu range in vehicle configuration file");
			System.exit(-1);
		    }
		    String ecuIdStr = idElement.getTextContent();
		    System.out.println("  <id>" + ecuIdStr + "</id>");
		    int ecuId = Integer.parseInt(ecuIdStr);

		    String q4 = "insert into Ecu (ecuID,vehicleConfig_id) values (" + ecuId + "," + c3 + ")";
		    rows = CallMySql.update(q4);

		    String q41 = "select id from Ecu where vehicleConfig_id = " + c3;
		    String c41 = CallMySql.getOne(q41);

		    // swcs
		    Element swcsElement = (Element) ecuElement
			.getElementsByTagName("swcs").item(0);
		    if (swcsElement == null) {
			System.out.println("There is no swcs element in ecu range in vehicle configuration file");
			System.exit(-1);
		    }
		    NodeList swcList = swcsElement.getElementsByTagName("swc");
		    for (int s = 0; s < swcList.getLength(); s++) {
			// swc
			Element swcElement = (Element) swcList.item(s);
			if (swcElement == null) {
			    System.out.println
				("There is no swc element " +
				 "in ecu range in vehicle configuration file");
			    System.exit(-1);
			}
			// hasPirte
			Element hasPirteElement = (Element) swcElement
			    .getElementsByTagName("hasPirte").item(0);
			if (hasPirteElement == null) {
			    System.out
				.println("There is no hasPirte in ecu range in vehicle configuration file");
			    System.exit(-1);
			}
			String hasPirteStr = hasPirteElement.getTextContent();
			if (hasPirteStr.equals("true")) {
			    // ports
			    Element portsElement = (Element) swcElement
				.getElementsByTagName("ports").item(0);
			    if (portsElement == null) {
				System.out
				    .println("There is no ports element in ecu range in vehicle configuraiton file");
				System.exit(-1);
			    }
			    NodeList portsList = portsElement
				.getElementsByTagName("port");
			    for (int j = 0; j < portsList.getLength(); j++) {
				// port
				Element portElement = (Element) portsList.item(j);

				// port ID
				Element portIdElement = (Element) portElement
				    .getElementsByTagName("id").item(0);
				if (portIdElement == null) {
				    System.out
					.println("There is no id element in port range in vehicle configuration file");
				    System.exit(-1);
				}
				String portIdStr = portIdElement.getTextContent();
				int portId = Integer.parseInt(portIdStr);

				portId2EcuId.put(portId, ecuId);

				System.out.println("      <id>" + portIdStr
						   + "</id>");
				System.out.println("    </port>");

				String q5 = "insert into Port (portId,ecu_id) values (" + portId + "," + c41 + ")";
				rows = CallMySql.update(q5);
			    }

			    System.out.println("  </ports>");
			}
		    }
		}
		System.out.println("</ecu>");

		// links
		NodeList links = root.getElementsByTagName("links");

		for (int i = 0; i < links.getLength(); i++) {
		    // link
		    Element linkElement = (Element) links.item(i);

		    // type
		    Element typeElement = (Element) linkElement
			.getElementsByTagName("type").item(0);
		    if (typeElement == null) {
			System.out
			    .println("There is no type element in link range in vehicle configuration file");
			System.exit(-1);
		    }
		    String typeStr = typeElement.getTextContent();
		    int type = Integer.parseInt(typeStr);

		    // fromPort
		    Element fromPortElement = (Element) linkElement
			.getElementsByTagName("from").item(0);
		    if (fromPortElement == null) {
			System.out
			    .println("There is no from element in link range in vehicle configuration file");
			System.exit(-1);
		    }
		    String fromPortStr = fromPortElement.getTextContent();
		    int fromPortId = Integer.parseInt(fromPortStr);

		    //				Integer fromEcuId;
		    //				if (fromPortId >= 100)
		    //					fromEcuId = -1;
		    //				else
		    //					fromEcuId = portId2EcuId.get(fromPortId);
		    System.out.println("table " + portId2EcuId);
		    int fromEcuId;
		    try {
			fromEcuId = portId2EcuId.get(fromPortId);
		    } catch (NullPointerException e) {
			System.out.println(portId2EcuId + " is not in the table");
			throw e;
		    }
				
		    // toPort
		    Element toPortElement = (Element) linkElement
			.getElementsByTagName("to").item(0);
		    if (toPortElement == null) {
			System.out
			    .println("There is no to element in link range vehicle configuration file");
			System.exit(-1);
		    }
		    String toPortStr = toPortElement.getTextContent();
		    int toPortId = Integer.parseInt(toPortStr);

		    //				Integer toEcuId;
		    //				if (toPortId >= 100)
		    //					toEcuId = -1;
		    //				else
		    //					toEcuId = portId2EcuId.get(toPortId);
		    int toEcuId = portId2EcuId.get(toPortId);
				
		    String q7 = "insert into Link (fromEcuId,toEcuId,fromPortId,toPortId,type,vehicleConfig_id) values (" + fromEcuId +
			"," + toEcuId +
			"," + fromPortId +
			"," + toPortId +
			"," + type +
			"," + c3 + ")";
		    rows = CallMySql.update(q7);

		    System.out.println("Done saving config!!!!!!");
		}

	    } catch (ParserConfigurationException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (SAXException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }

	    return "true";

	}

	private boolean isPluginPort(String portName,
			HashMap<String, PluginConfig> portName2PluginConfigs) {
		return portName2PluginConfigs.containsKey(portName);
	}

	@WebMethod
	public String generateSuite(String zipFile, String fullClassName)
			throws PluginWebServicesException {
		CompressUtils util = new CompressUtils();
		System.out.println("Calling unzip on " + zipFile);
		String dest = util.unzip(zipFile);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Unzipped into: " + dest); 
		dest = dest.substring(0,  dest.length() - 11); //Remove "j2meclasses"
		
		String reply = suiteGen.generateSuite(dest); // + "/" + fullClassName);
		return reply;
	}

}