package burp;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Paths;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedObject;


/**
 * Entry point of the extension
 */
public class BurpExtender implements BurpExtension {

    /**
     * The MontoyaAPI object used for accessing all the Burp features and ressources such as requests and responses.
     */
    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        PersistedObject preferences = this.api.persistence().extensionData();
        ConfigMenu configMenu = null;
        String extensionName = "LogRequestsToSQLite";
        JFrame burpFrame = ConfigMenu.getBurpFrame();

        String customStoreFileName = preferences.getString(ConfigMenu.DB_FILE_CUSTOM_LOCATION_CFG_KEY);
        try {
            //Extension init.
            this.api.extension().setName(extensionName);
            Trace trace = new Trace(this.api);
            //If the logging is not paused then ask to the user if he want to continue to log the events in the current DB file or pause the logging
            // Boolean isLoggingPaused = Boolean.TRUE.equals(preferences.getBoolean(ConfigMenu.PAUSE_LOGGING_CFG_KEY));
            while (!Boolean.TRUE.equals(preferences.getBoolean(ConfigMenu.PAUSE_LOGGING_CFG_KEY)) && (customStoreFileName == null || !Files.exists(Paths.get(customStoreFileName)))) {
                if(customStoreFileName != null){
                    trace.writeLog("Previously stored DB file does not exist anymore ('" + customStoreFileName + "')");
                }
                Object[] options = {"Select DB file", "Pause Logging"};
                String msg = "No DB is selected for this project. Please choose a DB file to enable logging";

                int loggingQuestionReply = JOptionPane.showOptionDialog(burpFrame, msg, extensionName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                if (loggingQuestionReply == JOptionPane.OK_OPTION) {
                    JFileChooser customStoreFileNameFileChooser = Utilities.createDBFileChooser();
                    int dbFileSelectionReply = customStoreFileNameFileChooser.showDialog(burpFrame, "Use");
                    if (dbFileSelectionReply == JFileChooser.APPROVE_OPTION) {
                        customStoreFileName = customStoreFileNameFileChooser.getSelectedFile().getAbsolutePath().replaceAll("\\\\", "/");
                        preferences.setString(ConfigMenu.DB_FILE_CUSTOM_LOCATION_CFG_KEY, customStoreFileName);
                    }
                } else {
                    this.api.logging().logToOutput("No DB was chosen. Logging is disabled.");
                    preferences.setBoolean(ConfigMenu.PAUSE_LOGGING_CFG_KEY, Boolean.TRUE);
                    // isLoggingPaused = Boolean.TRUE;
                }
            }
            ActivityLogger activityLogger = new ActivityLogger(this.api, trace);

            if (customStoreFileName != null) {
                activityLogger.updateStoreLocation(customStoreFileName);
                trace.writeLog("Updated StoreLocation");
            }

            ActivityHttpListener activityHttpListener = new ActivityHttpListener(activityLogger, trace);
            //Setup the configuration menu
            configMenu = new ConfigMenu(this.api, trace, activityLogger);
            SwingUtilities.invokeLater(configMenu);
            //Register all listeners
            this.api.http().registerHttpHandler(activityHttpListener);
            this.api.extension().registerUnloadingHandler(activityLogger);
        } catch (Exception e) {
            String errMsg = "Cannot start the extension due to the following reason:\n\r" + e.getMessage();
            //Notification of the error in the dashboard tab
            this.api.logging().raiseErrorEvent(errMsg);
            //Notification of the error using the UI
            JOptionPane.showMessageDialog(burpFrame, errMsg, extensionName, JOptionPane.ERROR_MESSAGE);
        }
    }
}
