/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.feature.vt.gui.wizard;

import static ghidra.framework.main.DataTreeDialogType.*;

import java.awt.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.lang3.StringUtils;

import docking.widgets.button.BrowseButton;
import docking.widgets.label.GDLabel;
import docking.wizard.*;
import generic.theme.GIcon;
import generic.theme.GThemeDefaults.Ids.Fonts;
import generic.theme.Gui;
import ghidra.app.util.task.OpenProgramRequest;
import ghidra.app.util.task.OpenProgramTask;
import ghidra.feature.vt.api.util.VTSessionFileUtil;
import ghidra.framework.main.DataTreeDialog;
import ghidra.framework.model.DomainFile;
import ghidra.framework.model.DomainFolder;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.listing.Program;
import ghidra.util.*;
import ghidra.util.task.TaskLauncher;

/**
 * Version tracking wizard panel to create a new session.
 */
public class NewSessionPanel extends AbstractMageJPanel<VTWizardStateKey> {

	// The maximum length to allow for each program's name portion of the session name.
	// In the filesystem API, when saved, the session name is restricted to 60 characters.
	// The default VTSession name combines the two program names so split the length between them, 
	// minus text we add below.
	private static final int VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH = 28;
	private static final int TEXT_FIELD_LENGTH = 40;
	private static final Icon SWAP_ICON = new GIcon("icon.version.tracking.new.session.swap");
	private static final Icon INFO_ICON = new GIcon("icon.version.tracking.new.session.info");

	private JTextField sourceField;
	private JTextField destinationField;
	private JButton sourceBrowseButton;
	private JButton destinationBrowseButton;
	private JButton swapProgramsButton;
	private JTextField sessionNameField;
	private JTextField folderNameField;
	private DomainFolder folder;
	private PluginTool tool;

	// All program info objects that the user may have opened while using the wizard.  We keep
	// these around to avoid reopening them and any accompanying upgrading that may be required.
	// These will be released when the wizard is finished.
	private Map<DomainFile, ProgramInfo> allProgramInfos = new HashMap<>();
	private ProgramInfo sourceProgramInfo;
	private ProgramInfo destinationProgramInfo;

	NewSessionPanel(PluginTool tool) {

		this.tool = tool;
		setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

		JLabel folderLabel = new GDLabel("Project folder ");
		folderLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		folderLabel.setToolTipText("The folder to store the new Version Tracking Session");
		folderNameField = new JTextField();
		Gui.registerFont(folderNameField, Fonts.MONOSPACED);
		folderNameField.setEditable(false); // force user to browse to choose

		JButton browseFolderButton = new BrowseButton();
		browseFolderButton.addActionListener(e -> browseDataTreeFolders());

		JLabel newSessionLabel = new GDLabel("New Session Name: ");
		newSessionLabel.setToolTipText("The name for the new Version Tracking Session");
		newSessionLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		sessionNameField = new JTextField(TEXT_FIELD_LENGTH);
		sessionNameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				// do nothing
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				notifyListenersOfValidityChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				notifyListenersOfValidityChanged();
			}
		});

		JLabel sourceLabel = new GDLabel("Source Program: ");
		sourceLabel.setIcon(INFO_ICON);
		sourceLabel.setToolTipText("Analyzed program with markup to transfer");
		sourceLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		JLabel destinationLabel = new GDLabel("Destination Program: ");
		destinationLabel.setIcon(INFO_ICON);
		destinationLabel.setToolTipText("New program that receives the transferred markup");
		destinationLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		sourceField = new JTextField(TEXT_FIELD_LENGTH);
		sourceField.setEditable(false);

		destinationField = new JTextField(TEXT_FIELD_LENGTH);
		destinationField.setEditable(false);

		sourceBrowseButton = createSourceBrowseButton();
		destinationBrowseButton = createDestinationBrowseButton();

		swapProgramsButton = new JButton(SWAP_ICON);
		swapProgramsButton.setText("swap");
		swapProgramsButton.setName("SWAP_BUTTON");
		swapProgramsButton.addActionListener(arg0 -> swapPrograms());

		JPanel mainPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		gbc.gridx = 0;
		gbc.gridy = 0;
		mainPanel.add(Box.createVerticalStrut(15), gbc);

		gbc.gridy++;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		mainPanel.add(folderLabel, gbc);

		gbc.gridx++;
		mainPanel.add(folderNameField, gbc);

		gbc.gridx++;
		mainPanel.add(Box.createHorizontalStrut(5), gbc);

		gbc.gridx++;
		mainPanel.add(browseFolderButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		mainPanel.add(Box.createVerticalStrut(10), gbc);

		gbc.gridy++;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		mainPanel.add(newSessionLabel, gbc);

		gbc.gridx++;
		mainPanel.add(sessionNameField, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		mainPanel.add(Box.createVerticalStrut(15), gbc);

		gbc.gridy++;
		gbc.gridwidth = 4;
		mainPanel.add(new JSeparator(), gbc);

		gbc.gridy++;
		gbc.gridwidth = 1;
		mainPanel.add(Box.createVerticalStrut(25), gbc);

		gbc.gridy++;
		mainPanel.add(sourceLabel, gbc);

		gbc.gridx++;
		mainPanel.add(sourceField, gbc);

		gbc.gridx += 2;
		mainPanel.add(sourceBrowseButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.fill = GridBagConstraints.NONE;
		gbc.gridwidth = 4;
		mainPanel.add(swapProgramsButton, gbc);

		gbc.gridwidth = 1;
		gbc.gridy++;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		mainPanel.add(destinationLabel, gbc);

		gbc.gridx++;
		mainPanel.add(destinationField, gbc);

		gbc.gridx += 2;
		mainPanel.add(destinationBrowseButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		mainPanel.add(Box.createVerticalStrut(25), gbc);

		gbc.gridy++;
		gbc.gridwidth = 4;
		mainPanel.add(new JSeparator(), gbc);

		gbc.gridy++;
		gbc.gridwidth = 1;
		mainPanel.add(Box.createVerticalStrut(60), gbc);

		setLayout(new BorderLayout());
		add(mainPanel, BorderLayout.NORTH);
	}

	private void initializePrograms(WizardState<VTWizardStateKey> state) {
		DomainFile source = (DomainFile) state.get(VTWizardStateKey.SOURCE_PROGRAM_FILE);
		DomainFile destintation = (DomainFile) state.get(VTWizardStateKey.DESTINATION_PROGRAM_FILE);

		if (source != null) {
			setSourceProgram(source);
		}
		if (destintation != null) {
			setDestinationProgram(destintation);
		}
	}

	/**
	 * Presents the user with a tree of the existing project folders and allows
	 * them to pick one
	 */
	private void browseDataTreeFolders() {
		final DataTreeDialog dataTreeDialog =
			new DataTreeDialog(this, "Choose a project folder", CHOOSE_FOLDER);

		dataTreeDialog.addOkActionListener(e -> {
			dataTreeDialog.close();
			setFolder(dataTreeDialog.getDomainFolder());
		});
		dataTreeDialog.showComponent();
	}

	void setFolder(DomainFolder folder) {
		this.folder = folder;

		if (folder != null) {
			folderNameField.setText(folder.toString());
		}
		else {
			folderNameField.setText("< Choose a folder >");
		}

		notifyListenersOfValidityChanged();
	}

	private void setSourceProgram(DomainFile programFile) {
		notifyListenersOfStatusMessage(" ");

		String path;
		if (programFile == null) {
			sourceProgramInfo = null;
			path = "";
		}
		else {
			sourceProgramInfo =
				allProgramInfos.computeIfAbsent(programFile, file -> new ProgramInfo(file));
			path = programFile.getPathname();
		}

		sourceField.setText(path);

		updateSessionNameIfBlank();
		notifyListenersOfValidityChanged();
	}

	private void updateSessionNameIfBlank() {
		if (!StringUtils.isBlank(sessionNameField.getText())) {
			return;
		}
		if (sourceProgramInfo == null || destinationProgramInfo == null) {
			return;
		}

		String defaultSessionName =
			createVTSessionName(sourceProgramInfo.getName(), destinationProgramInfo.getName());
		sessionNameField.setText(defaultSessionName);
	}

	private String createVTSessionName(String sourceName, String destinationName) {

		// if together they are within the bounds just return session name with both full names
		if (sourceName.length() + destinationName.length() <= 2 *
			VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH) {
			return "VT_" + sourceName + "_" + destinationName;
		}

		// give destination name all space not used by source name 
		if (sourceName.length() < VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH) {
			int leftover = VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH - sourceName.length();
			destinationName = StringUtilities.trimMiddle(destinationName,
				VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH + leftover);
			return "VT_" + sourceName + "_" + destinationName;
		}

		// give source name all space not used by destination name 
		if (destinationName.length() < VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH) {
			int leftover = VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH - destinationName.length();
			sourceName = StringUtilities.trimMiddle(sourceName,
				VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH + leftover);
			return "VT_" + sourceName + "_" + destinationName;
		}

		// if both too long, shorten both of them
		sourceName = StringUtilities.trimMiddle(sourceName, VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH);
		destinationName =
			StringUtilities.trimMiddle(destinationName, VTSESSION_NAME_PROGRAM_NAME_MAX_LENGTH);

		return "VT_" + sourceName + "_" + destinationName;
	}

	private void setDestinationProgram(DomainFile programFile) {
		notifyListenersOfStatusMessage(" ");

		String path;
		if (programFile == null) {
			destinationProgramInfo = null;
			path = "";
		}
		else {
			destinationProgramInfo =
				allProgramInfos.computeIfAbsent(programFile, file -> new ProgramInfo(file));
			path = programFile.getPathname();
		}

		destinationField.setText(path);
		updateSessionNameIfBlank();
		notifyListenersOfValidityChanged();
	}

	private void swapPrograms() {
		notifyListenersOfStatusMessage(" ");

		ProgramInfo temp = destinationProgramInfo;
		destinationProgramInfo = sourceProgramInfo;
		sourceProgramInfo = temp;

		if (sourceProgramInfo != null) {
			sourceField.setText(sourceProgramInfo.getPathname());
		}
		else {
			sourceField.setText("");
		}

		if (destinationProgramInfo != null) {
			destinationField.setText(destinationProgramInfo.getPathname());
		}
		else {
			destinationField.setText("");
		}

		notifyListenersOfValidityChanged();
	}

	@Override
	public HelpLocation getHelpLocation() {
		return new HelpLocation("VersionTrackingPlugin", "New_Session_Panel");
	}

	private void releaseConsumers() {

		for (ProgramInfo info : allProgramInfos.values()) {
			info.release(tool);
		}

		allProgramInfos.clear();
	}

	@Override
	public void enterPanel(WizardState<VTWizardStateKey> state) {
		initializePrograms(state);
	}

	@Override
	public WizardPanelDisplayability getPanelDisplayabilityAndUpdateState(
			WizardState<VTWizardStateKey> state) {
		return WizardPanelDisplayability.MUST_BE_DISPLAYED;
	}

	@Override
	public void leavePanel(WizardState<VTWizardStateKey> state) {
		updateStateObjectWithPanelInfo(state);
	}

	@Override
	public void updateStateObjectWithPanelInfo(WizardState<VTWizardStateKey> state) {
		state.put(VTWizardStateKey.SOURCE_PROGRAM_FILE, sourceProgramInfo.getFile());
		state.put(VTWizardStateKey.DESTINATION_PROGRAM_FILE, destinationProgramInfo.getFile());
		state.put(VTWizardStateKey.SOURCE_PROGRAM, sourceProgramInfo.getProgram());
		state.put(VTWizardStateKey.DESTINATION_PROGRAM, destinationProgramInfo.getProgram());
		state.put(VTWizardStateKey.SESSION_NAME, sessionNameField.getText());
		state.put(VTWizardStateKey.NEW_SESSION_FOLDER, folder);
	}

	private boolean openProgram(ProgramInfo programInfo) {

		if (programInfo.hasProgram()) {
			return true; // already open
		}

		OpenProgramTask openProgramTask = new OpenProgramTask(programInfo.getFile(), tool);
		new TaskLauncher(openProgramTask, tool.getActiveWindow());
		OpenProgramRequest openProgram = openProgramTask.getOpenProgram();
		programInfo.setProgram(openProgram != null ? openProgram.getProgram() : null);
		return programInfo.hasProgram();
	}

	@Override
	public String getTitle() {
		return "New Version Tracking Session";
	}

	@Override
	public void initialize() {
		sourceProgramInfo = null;
		destinationProgramInfo = null;
		sessionNameField.setText("");
		sourceField.setText("");
		destinationField.setText("");
		setFolder(tool.getProject().getProjectData().getRootFolder());
	}

	@Override
	public boolean isValidInformation() {

		if (folder == null) {
			notifyListenersOfStatusMessage("Choose a project folder to continue!");
			return false;
		}

		if (sourceProgramInfo == null || destinationProgramInfo == null) {
			return false;
		}

		if (sourceProgramInfo.hasSameFile(destinationProgramInfo)) {
			notifyListenersOfStatusMessage("Source and Destination Programs must be different");
			releaseConsumers();
			return false;
		}

		String name = sessionNameField.getText().trim();
		if (StringUtils.isBlank(name)) {
			notifyListenersOfStatusMessage("Please enter a name for this session");
			return false;
		}

		try {
			tool.getProject().getProjectData().testValidName(name, false);
		}
		catch (InvalidNameException e) {
			notifyListenersOfStatusMessage("'" + name + "' contains invalid characters");
			return false;
		}

		DomainFile file = folder.getFile(name);
		if (file != null) {
			notifyListenersOfStatusMessage(
				"'" + file.getPathname() + "' is the name of an existing project file");
			return false;
		}

		// Known Issue: Opening programs before comitted to using them (i.e., Next is clicked) seems 
		// premature and will subject user to prompts about possible checkout and/or upgrades 
		// with possible slow re-disassembly (see GP-4151)

		if (!isValidDestinationProgramFile() || !isValidSourceProgramFile()) {
			return false;
		}

		if (!openProgram(sourceProgramInfo)) {
			notifyListenersOfStatusMessage(
				"Can't open source program " + sourceProgramInfo.getName());
			return false;
		}

		if (!openProgram(destinationProgramInfo)) {
			notifyListenersOfStatusMessage(
				"Can't open destination program " + destinationProgramInfo.getName());
			return false;
		}

		notifyListenersOfStatusMessage(" ");
		return true;
	}

	private boolean isValidSourceProgramFile() {
		try {
			VTSessionFileUtil.validateSourceProgramFile(sourceProgramInfo.file, false);
		}
		catch (Exception e) {
			notifyListenersOfStatusMessage(e.getMessage());
			return false;
		}
		return true;
	}

	private boolean isValidDestinationProgramFile() {
		try {
			VTSessionFileUtil.validateDestinationProgramFile(destinationProgramInfo.file, false,
				false);
		}
		catch (Exception e) {
			notifyListenersOfStatusMessage(e.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public void addDependencies(WizardState<VTWizardStateKey> state) {
		// none
	}

	private JButton createSourceBrowseButton() {
		JButton button = new BrowseButton();
		button.setName("SOURCE_BUTTON");
		button.addActionListener(e -> {
			DomainFile programFile = VTWizardUtils.chooseDomainFile(NewSessionPanel.this,
				"a source program", VTWizardUtils.PROGRAM_FILTER, null);
			if (programFile != null) {
				setSourceProgram(programFile);
			}
		});
		return button;
	}

	private JButton createDestinationBrowseButton() {
		JButton button = new BrowseButton();
		button.setName("DESTINATION_BUTTON");
		button.addActionListener(e -> {
			DomainFile programFile = VTWizardUtils.chooseDomainFile(NewSessionPanel.this,
				"a destination program", VTWizardUtils.PROGRAM_FILTER, null);
			if (programFile != null) {
				setDestinationProgram(programFile);
			}
		});
		return button;
	}

	@Override
	public void dispose() {
		releaseConsumers();
	}

	// simple object to track a domain file and its program
	private class ProgramInfo {

		private Program program;
		private DomainFile file;

		public ProgramInfo(DomainFile file) {
			this.file = Objects.requireNonNull(file);
		}

		void setProgram(Program program) {
			this.program = program;
		}

		Program getProgram() {
			return program;
		}

		DomainFile getFile() {
			return file;
		}

		String getPathname() {
			return file.getPathname();
		}

		String getName() {
			return file.getName();
		}

		void release(Object consumer) {
			if (program == null) {
				return;
			}

			if (program.getConsumerList().contains(consumer)) {
				program.release(consumer);
			}

			program = null;
		}

		boolean hasSameFile(ProgramInfo other) {
			return file.getPathname().equals(other.getPathname());
		}

		boolean hasProgram() {
			return program != null;
		}
	}
}
