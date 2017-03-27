package no.artorp.profileio.javafx;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import no.artorp.profileio.javafx.mainwindowcells.FacVersionNameCell;
import no.artorp.profileio.javafx.mainwindowcells.ProfileIsActiveTableCell;
import no.artorp.profileio.javafx.mainwindowcells.ProfileNameCell;
import no.artorp.profileio.json_models.SettingsJson;
import no.artorp.profileio.utility.DirectoryWatcher;
import no.artorp.profileio.utility.FileIO;
import no.artorp.profileio.utility.Globals;
import no.artorp.profileio.utility.ProfileDirectoryHelper;
import no.artorp.profileio.utility.SettingsIO;
import no.artorp.profileio.utility.WatcherListener;

public class MainWindowController implements WatcherListener {

	private final Stage primaryStage;
	private Stage settingsStage;
	private final SettingsIO settingsIO;
	private final FileIO fileIO;
	private final Registry myRegistry;
	private final ObservableList<Profile> tableData;
	private final Set<Path> ignoreFileEvents = new HashSet<>();
	private DirectoryWatcher activeWatcher;
	private Thread watcherThread;
	
	@FXML private TableView<Profile> tableViewProfiles;
	@FXML private TableColumn<Profile, Profile> columnName;
	@FXML private TableColumn<Profile, String> columnFactorioVersion;
	@FXML private TableColumn<Profile, Boolean> columnSetActive;
	@FXML private Button buttonNewProfile;
	@FXML private Button buttonBrowse;
	@FXML private Button buttonRefresh;
	@FXML private Button buttonSettings;
	@FXML private Button buttonStartFactorio;
	
	
	
	public MainWindowController(Stage primaryStage, SettingsIO settingsIO, FileIO fileIO, Registry myRegistry) {
		this.primaryStage = primaryStage;
		this.settingsIO = settingsIO;
		this.fileIO = fileIO;
		this.myRegistry = myRegistry;
		this.tableData = FXCollections.observableArrayList(
				(Profile profile) -> new Observable[] { profile.customNameProperty() }
				);
		
		// Registry object needs tabledata to set up bindings, pass it over
		myRegistry.setupPathProfileBindings(tableData);
		
	}
	
	public void initialize() {
		// Load settings if found
		SettingsJson settings = null;
		if (! settingsIO.getSettingsFile().exists()) {
			// First time application launched on this system
			// Use runLater() to ensure primaryStage is properly initialized and showed
			Platform.runLater(() -> {
				try {
					settingsIO.initializeRegistry(myRegistry);
					fileIO.createProfilesDir(myRegistry);
					openSettingsWindow();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		} else {
			try {
				settings = settingsIO.loadSettings();
				settingsIO.putIntoRegistry(settings, myRegistry);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		
		// Setup TableView  |new PropertyValueFactory<>("profileName")
		columnName.setCellValueFactory((CellDataFeatures<Profile, Profile> f) -> {
			return new SimpleObjectProperty<Profile>(f.getValue());
			});
		columnName.setCellFactory(tableColumn->new ProfileNameCell(this));
		//columnName.setSortType(SortType.ASCENDING);
		
		// Columns are editable through combobox, and are in red text if invalid choice
		columnFactorioVersion.setCellValueFactory(new PropertyValueFactory<>("factorioVersion"));
		columnFactorioVersion.setCellFactory(columnFeature->new FacVersionNameCell(myRegistry));
		
		// Toggle buttons for which profile is active
		ToggleGroup activeGroup = new ToggleGroup();
		columnSetActive.setCellValueFactory(new PropertyValueFactory<>("isActive"));
		columnSetActive.setCellFactory(columnFeature->new ProfileIsActiveTableCell(activeGroup, myRegistry, fileIO, settingsIO));
		
		tableViewProfiles.setSortPolicy(t -> {
			FXCollections.sort(t.getItems(), new ProfileComparator());
			return true;
		});
		
		
		if (myRegistry.getHasInitialized().booleanValue()) {
			refreshProfiles(); // If loaded from settings
			this.setupDirectoryWatcher(myRegistry.getFactorioProfilesPath());
		}
		
		tableViewProfiles.setItems(tableData);
		tableViewProfiles.sort();
		
		
		// Browse button disable state
		tableViewProfiles.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
			boolean nothingSelected = (newValue.intValue() == -1);
			buttonBrowse.setDisable(nothingSelected);
		});
		
		// Can only start if active profile set and valid factorio version
		this.myRegistry.activeProfileProperty().addListener((ob, o, n) -> {
			boolean disabled = true;
			if (n != null) {
				FactorioInstallations fi = n.getFactorioInstallation();
				if (fi != null) {
					// If version is in list, enable button
					disabled = ! myRegistry.getFactorioInstallations().contains(n.getFactorioInstallation());
				}
			}
			buttonStartFactorio.setDisable(disabled);
		});
		
		
		
		// Setup button actions
		buttonNewProfile.setOnAction(event -> {
			TextInputDialog prompt = new TextInputDialog();
			prompt.setTitle("Enter name for the new profile");
			prompt.setHeaderText(null);
			prompt.setContentText("New profile name:");
			prompt.getEditor().textProperty().addListener((observable, oldVal, newVal) -> {
				if (newVal != null) {
					Node confirm = prompt.getDialogPane().lookupButton(ButtonType.OK);
					boolean alreadyInUse = false;
					for (Profile p : tableData) {
						if (p.getName().equalsIgnoreCase(newVal)) {
							alreadyInUse = true;
							break;
						}
					}
					if (alreadyInUse) {
						confirm.setDisable(true);
						prompt.getEditor().setStyle(
								"-fx-focus-color: rgba(255, 0, 0, 0.8); -fx-faint-focus-color:rgba(255, 100, 100, 0.2);"
								);
	        		} else {
						confirm.setDisable(false);
						prompt.getEditor().setStyle("");
					}
				}
			});
			prompt.getEditor().setText("new profile");
			Optional<String> result = prompt.showAndWait();
			String name = null;
			if (result.isPresent()) {
				name = result.get();
			} else {
				return; // User cancelled
			}
			Path profilePath = myRegistry.getFactorioProfilesPath().resolve(name);
			
			if (profilePath.toFile().exists())
				return;
			
			Path mods = profilePath.resolve(SettingsIO.FOLDER_NAME_MODS);
			Path saves = profilePath.resolve(SettingsIO.FOLDER_NAME_SAVES);
			
			mods.toFile().mkdirs();
			saves.toFile().mkdirs();
		});
		
		buttonBrowse.setOnAction(event->{
			try {
				File file = tableViewProfiles.getSelectionModel().getSelectedItem().getDirectory();
				if (file.exists()) {
					Desktop.getDesktop().browse(file.toURI());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		
		buttonRefresh.setOnAction(event -> {
			this.refreshProfiles();
		});

		buttonRefresh.setDisable(! myRegistry.getHasInitialized().booleanValue());
		buttonNewProfile.setDisable(! myRegistry.getHasInitialized().booleanValue());
		
		myRegistry.hasInitializedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue.booleanValue()) {
				buttonRefresh.setDisable(false);
				buttonNewProfile.setDisable(false);
			}
		});
		
		buttonSettings.setOnAction(event->{
			try {
				openSettingsWindow();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		
		buttonStartFactorio.setOnAction(event->{
			List<String> commands = new ArrayList<>();
			Profile activeProfile = myRegistry.getActiveProfile();
			if (activeProfile == null) {
				System.err.println("No active profile found.");
				return;
			}
			String factorioName = activeProfile.getFactorioVersion();
			Path factorioPath = null;
			for (FactorioInstallations fi : myRegistry.getFactorioInstallations()) {
				if (fi.getName().equals(factorioName)) {
					factorioPath = fi.getPath();
				}
			}
			if (factorioPath == null) {
				System.err.println("No factorio installations found.");
				return;
			}
			commands.add(factorioPath.toAbsolutePath().toString());
			ProcessBuilder pBuilder = new ProcessBuilder(commands);
			try {
				pBuilder.start();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			if (myRegistry.getCloseOnLaunch().booleanValue()) {
				Platform.exit();
			}
		});
	}

	
	/**
	 * Setup new stage if unset, and open settings window
	 * <p>
	 * Will focus existing settings window if it is showing
	 * 
	 * @throws IOException 
	 */
	private void openSettingsWindow() throws IOException {
		if (settingsStage != null) {
			if (settingsStage.isShowing()) {
				settingsStage.requestFocus();
			} else {
				settingsStage.show();
			}
		} else {
			settingsStage = new Stage();
			
			settingsStage.getIcons().addAll( primaryStage.getIcons() );
			
			//settingsStage.initModality(Modality.WINDOW_MODAL);
			settingsStage.setTitle("Settings");
			settingsStage.initOwner(primaryStage);
			settingsStage.initStyle(StageStyle.UTILITY);
			
			FXMLLoader settingsLoader = new FXMLLoader(getClass().getResource("/Settings.fxml"));
			SettingsController settingsController = new SettingsController(
					primaryStage, settingsStage, this, settingsIO, fileIO, myRegistry, this.tableData);
			settingsLoader.setController(settingsController);
			BorderPane root = (BorderPane) settingsLoader.load();
			
			Scene scene = new Scene(root);
			// scene.getStylesheets().add("/style.css");
			settingsStage.setScene(scene);
			settingsStage.show();
			settingsStage.requestFocus();
		}
	}
	
	public void refreshProfiles() {
		List<Profile> newProfiles = ProfileDirectoryHelper.getProfiles(myRegistry, settingsIO);
		tableData.setAll(newProfiles);
		tableViewProfiles.sort();
	}

	@Override
	public void fileDeleted(Path fileDeleted) {
		if (Platform.isFxApplicationThread()) {
			fileDeletedHandle(fileDeleted);
		} else {
			Platform.runLater(() -> fileDeletedHandle(fileDeleted));
		}
	}
	
	private void fileDeletedHandle(Path fileDeleted) {
		if (checkIfIgnore(fileDeleted)) return;
		if (fileDeleted.equals(myRegistry.getActiveProfilePath())) {
			if (myRegistry.getMoveMethod() == FileIO.METHOD_JUNCTION) {
				// Delete old links, they don't point to anything anymore
				try {
					fileIO.revertProfileJunctions(myRegistry.getFactorioDataPath());
				} catch (IOException e) {
					Alert alert = new ExceptionDialog(e);
					alert.showAndWait();
					e.printStackTrace();
				}
			} else if (myRegistry.getMoveMethod() == FileIO.METHOD_SYMLINK) {
				// Same with symlinks
				try {
					fileIO.revertProfileSymlinks(myRegistry.getFactorioDataPath());
				} catch (IOException e) {
					Alert alert = new ExceptionDialog(e);
					alert.showAndWait();
					e.printStackTrace();
				}
			} else if (myRegistry.getMoveMethod() == FileIO.METHOD_MOVE ) {
				// Maybe user renamed file within an explorer?
				// Renames not supported in move method unless
				// renamed from within application
				Alert alert = new Alert(AlertType.WARNING);
				alert.setHeaderText("Active profile folder lost.");
				alert.setContentText("Lost track of active profile: "+ fileDeleted
						+ "\n\nWas it renamed? "
						+ "\"mods\" and \"saves\" folder in user directory now out of sync. "
						+ "Please move them into an appropriate folder.");
				alert.showAndWait();
			}
		}
		Profile toDelete = null;
		for (Profile p : tableData) {
			if (p.getDirectory().toPath().equals(fileDeleted)) {
				toDelete = p;
				break;
			}
		}
		if (toDelete != null) {
			tableData.remove(toDelete);
			tableViewProfiles.sort();
		}
	}

	@Override
	public void fileCreated(Path fileCreated) {
		if (Platform.isFxApplicationThread()) {
			fileCreatedHandle(fileCreated);
		} else {
			Platform.runLater(() -> fileCreatedHandle(fileCreated));
		}
	}
	
	private void fileCreatedHandle(Path fileCreated) {
		if (checkIfIgnore(fileCreated)) return;
		// All new profiles are assignes as inactive
		if (! fileCreated.toFile().isDirectory()) return;
		Profile newProfile = new Profile(fileCreated.toFile(), myRegistry, false, settingsIO);
		if (! myRegistry.getFactorioInstallations().isEmpty()) {
			newProfile.setFactorioVersion(myRegistry.getFactorioInstallations().get(0).getName());
		}
		tableData.add(newProfile);
		tableViewProfiles.sort();
	}

	@Override
	public void fileModified(Path fileModified) {
		return; // Not interested in modifications
	}
	
	private boolean checkIfIgnore(Path path) {
		if (this.ignoreFileEvents.contains(path)) {
			this.ignoreFileEvents.remove(path);
			return true;
		}
		return false;
	}
	
	public void ignoreTheseEvents(Path...paths) {
		for (Path p : paths) {
			this.ignoreFileEvents.add(p);
		}
	}
	
	public void stopIgnoreTheseEvents(Path...paths) {
		for (Path p : paths) {
			this.ignoreFileEvents.remove(p);
		}
	}
	
	public void stopWatcher() {
		if (this.watcherThread != null && this.watcherThread.isAlive()) {
			this.watcherThread.interrupt();
			Globals.THREADS.remove(watcherThread);
		}
	}
	
	public void setupDirectoryWatcher(Path dir) {
		if (this.watcherThread != null && this.watcherThread.isAlive()) {
			this.watcherThread.interrupt();
			Globals.THREADS.remove(watcherThread);
		}
		
		try {
			this.activeWatcher = new DirectoryWatcher(dir, false);
			this.activeWatcher.addListener(this);
			this.watcherThread = new Thread(this.activeWatcher);
			this.watcherThread.setDaemon(true);
			this.watcherThread.start();
			
			Globals.THREADS.add(watcherThread);
		} catch (IOException e) {
			Alert alert = new ExceptionDialog(e);
			e.printStackTrace();
			alert.showAndWait();
		}
		
	}
	
}