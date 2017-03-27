package no.artorp.profileio.utility;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import no.artorp.profileio.javafx.ExceptionDialog;
import no.artorp.profileio.javafx.FactorioInstallations;
import no.artorp.profileio.javafx.KeyValuePair;
import no.artorp.profileio.javafx.Registry;
import no.artorp.profileio.json_models.FactorioInstallationsJson;
import no.artorp.profileio.json_models.FactorioVersionMapJson;
import no.artorp.profileio.json_models.SettingsJson;

/**
 * Handles loading and saving of settings
 */
public class SettingsIO {
	
	public static final String FOLDER_NAME_MODS = "mods";
	public static final String FOLDER_NAME_SAVES = "saves";
	
	private File settingsFile;

	public SettingsIO(File settingsFile) {
		this.settingsFile = settingsFile;
	}
	
	public void saveSettings(SettingsJson settingsObject) throws IOException {
		if (settingsObject == null) {
			throw new IllegalArgumentException("Settings object must be initialized.");
		}

		Gson gson = new GsonBuilder()
				.serializeNulls()
				.setPrettyPrinting()
				.create();
		String json_string = gson.toJson(settingsObject);
		try (Writer writer = new FileWriter(settingsFile, false)) {
			writer.write(json_string);
		}
	}
	
	public void saveRegistry(Registry r) {
		try {
			SettingsJson s = settingsFromRegistry(r);
			saveSettings(s);
		} catch (IOException e) {
			e.printStackTrace();
			Alert alert = new ExceptionDialog(e, "There was an error while saving settings.json");
			alert.showAndWait();
		}
	}
	
	public SettingsJson loadSettings() throws IOException {
		Gson gson = new Gson();
		byte[] json_bytes = Files.readAllBytes(settingsFile.toPath());
		String json_string = new String(json_bytes, StandardCharsets.UTF_8);
		SettingsJson settings = gson.fromJson(json_string, SettingsJson.class);
		return settings;
	}

	public File getSettingsFile() {
		return settingsFile;
	}

	public void setSettingsFile(File settingsFile) {
		this.settingsFile = settingsFile;
	}
	
	public void putIntoRegistry(SettingsJson settings, Registry myRegistry) {
		// Get from JSON object
		Path configPath = Paths.get(settings.configPath);
		Path factorioDataPath = Paths.get(settings.factorioDataPath);
		Path factorioProfilesPath = Paths.get(settings.factorioProfilesPath);
		Integer moveMethod = new Integer(settings.moveMethod);
		Boolean closeOnLaunch = new Boolean(settings.closeOnLaunch);
		Boolean hasInitialized = new Boolean(settings.hasInitialized);
		Path activeProfilePath = settings.activeProfilePath == null ? null : Paths.get(settings.activeProfilePath);
		
		ObservableList<FactorioInstallations> factorioInstallations = FXCollections.observableArrayList();
		List<FactorioInstallationsJson> installations = settings.factorioInstallations;
		for (FactorioInstallationsJson element : installations) {
			FactorioInstallations fi = new FactorioInstallations();
			fi.setName(element.customName);
			fi.setPath(Paths.get(element.path));
			factorioInstallations.add(fi);
		}
		
		ObservableList<KeyValuePair<String, String>> profileToFactorioName = FXCollections.observableArrayList();
		List<FactorioVersionMapJson> profileToInstallationMap = settings.profileToFactorioName;
		for (FactorioVersionMapJson e : profileToInstallationMap) {
			KeyValuePair<String, String> profileMap;
			profileMap = new KeyValuePair<String, String>(e.profileName, e.factorioName);
			profileToFactorioName.add(profileMap);
		}
		
		
		// Put in registry
		myRegistry.setConfigPath(configPath);
		myRegistry.setFactorioDataPath(factorioDataPath);
		myRegistry.setFactorioProfilesPath(factorioProfilesPath);
		myRegistry.setMoveMethod(moveMethod);
		myRegistry.setCloseOnLaunch(closeOnLaunch);
		myRegistry.setHasInitialized(hasInitialized);
		myRegistry.setActiveProfilePath(activeProfilePath);
		myRegistry.setFactorioInstallations(factorioInstallations);
		myRegistry.setProfileToFactorioName(profileToFactorioName);
	}
	
	public SettingsJson settingsFromRegistry(Registry myRegistry) {
		// Get from registry
		Path configPath = myRegistry.getConfigPath();
		Path factorioDataPath = myRegistry.getFactorioDataPath();
		Path factorioProfilesPath = myRegistry.getFactorioProfilesPath();
		Integer moveMethod = myRegistry.getMoveMethod();
		Boolean closeOnLaunch = myRegistry.getCloseOnLaunch();
		Boolean hasInitialized = myRegistry.getHasInitialized();
		Path activeProfilePath = myRegistry.getActiveProfilePath();
		ObservableList<FactorioInstallations> factorioInstallations = myRegistry.getFactorioInstallations();
		ObservableList<KeyValuePair<String, String>> profileToFactorioName = myRegistry.getProfileToFactorioName();
		
		// Generate Json object
		SettingsJson settings = new SettingsJson();
		settings.configPath = configPath.toString();
		settings.factorioDataPath = factorioDataPath.toString();
		settings.factorioProfilesPath = factorioProfilesPath.toString();
		settings.moveMethod = moveMethod.intValue();
		settings.closeOnLaunch = closeOnLaunch.booleanValue();
		settings.hasInitialized = hasInitialized.booleanValue();
		settings.activeProfilePath = activeProfilePath == null ? null : activeProfilePath.toString();
		
		List<FactorioInstallationsJson> installations = new ArrayList<>();
		for (FactorioInstallations fi : factorioInstallations) {
			FactorioInstallationsJson jsonObj = new FactorioInstallationsJson();
			jsonObj.customName = fi.getName();
			jsonObj.path = fi.getPath().toAbsolutePath().toString();
			installations.add(jsonObj);
		}
		settings.factorioInstallations = installations;
		
		List<FactorioVersionMapJson> profileToInstallationMap = new ArrayList<>();
		for (KeyValuePair<String, String> pair : profileToFactorioName) {
			FactorioVersionMapJson jsonObj = new FactorioVersionMapJson();
			jsonObj.profileName = pair.getKey();
			jsonObj.factorioName = pair.getValue();
			profileToInstallationMap.add(jsonObj);
		}
		settings.profileToFactorioName = profileToInstallationMap;
		
		return settings;
	}
	
	/**
	 * Set values of registry to the best match
	 * <p>
	 * Will attempt to find the best settings for the registry. Will
	 * overwrite previously set field values where applicable. Assumes
	 * registry has been constructed, but not loaded from settings file.
	 * @param registry instance of {@link Registry} object to be initialized
	 */
	public void initializeRegistry(Registry registry) {
		registry.setConfigPath(
				FileLocations.getConfigDirectory()
				);
		
		registry.setFactorioDataPath(
				FileLocations.getFactorioUserDataDirectory()
				);
		
		registry.setFactorioProfilesPath(
				FileLocations.getFactorioUserDataDirectory().resolve(FileLocations.DIR_PROFILE_NAME)
				);
		
		registry.setCloseOnLaunch(new Boolean(true));
		registry.setHasInitialized(new Boolean(false));
		
		
		// Try to find Factorio installation path
		List<Path> guesses = new ArrayList<>();
		if (FileLocations.isWindows()) {
			guesses.add(Paths.get("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Factorio"));
			guesses.add(Paths.get("C:\\Program Files\\Factorio"));
			
			for (int i = 0; i < guesses.size(); i++) {
				Path g = guesses.get(i);
				g = g.resolve(Paths.get("bin", "x64", "factorio.exe"));
				g = g.toAbsolutePath();
				guesses.set(i, g);
			}
		} else if (FileLocations.isMac()) {
			guesses.add(Paths.get(System.getProperty("user.home"),
					"Library/Application Support/Steam/steamapps/common/Factorio/factorio.app/Contents"));
			guesses.add(Paths.get("/Applications/factorio.app/Contents"));
			
			for (int i = 0; i < guesses.size(); i++) {
				Path g = guesses.get(i);
				g = g.resolve(Paths.get("MacOS", "factorio"));
				g = g.toAbsolutePath();
				guesses.set(i, g);
			}
		} else if (FileLocations.isLinuxUnix()) {
			guesses.add(Paths.get(System.getProperty("user.home"), ".factorio"));
			
			for (int i = 0; i < guesses.size(); i++) {
				Path g = guesses.get(i);
				g = g.resolve(Paths.get("bin", "x64", "factorio"));
				g = g.toAbsolutePath();
				guesses.set(i, g);
			}
		}
		// Check if any of the path guesses exists
		for (Path g : guesses) {
			if (g.toFile().exists()) {
				FactorioInstallations fi = new FactorioInstallations();
				fi.setName("Factorio");
				fi.setPath(g);
				registry.getFactorioInstallations().add(fi);
				System.out.println("Installation found!\n"+g);
				break;
			}
		}
		
		// Is there a profile folder? If so, populate profileToFactorioName
		File profileDir = registry.getFactorioProfilesPath().toFile();
		if (registry.getFactorioInstallations() != null &&
				registry.getFactorioInstallations().size() > 0 &&
				profileDir.exists()) {
			File[] children = profileDir.listFiles();
			if (children != null) {
				String factorioInstallation = registry.getFactorioInstallations().get(0).getName();
				for (File child : children) {
					String profileName = child.getName();
					KeyValuePair<String, String> pair = new KeyValuePair<String, String>(profileName, factorioInstallation);
					registry.getProfileToFactorioName().add(pair);
				}
			}
		}
	}

}