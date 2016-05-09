package ee.telia.iot.garage;

import org.kaaproject.kaa.client.DesktopKaaPlatformContext;
import org.kaaproject.kaa.client.Kaa;
import org.kaaproject.kaa.client.KaaClient;
import org.kaaproject.kaa.client.SimpleKaaClientStateListener;
import org.kaaproject.kaa.client.configuration.base.SimpleConfigurationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class GarageApplication {
    private static final Logger LOG = LoggerFactory.getLogger(GarageApplication.class);

    private static KaaClient client;
    private static DesktopKaaPlatformContext platformContext;
    private enum Mode {
        INVALID,
        DOOR,
        REMOTE
    };

    public static void main(String[] args) throws Exception {
        Mode mode;

        // check for startup parameters
        if (args.length < 1) {
            LOG.info("Please start the application as either a door or a remote");
            LOG.info("> java -jar GarageApplication.jar door");
            LOG.info("> java -jar GarageApplication.jar remote");

            return;
        }

        // detect requested mode
        switch (args[0]) {
            case "door":
                mode = Mode.DOOR;
                break;

            case "remote":
                mode = Mode.REMOTE;
                break;

            default:
                LOG.warn("invalid mode '" + args[0] + "' requested");

                mode = Mode.INVALID;
                break;
        }

        LOG.info("-- starting garage application --");

        // use desktop platform context
        platformContext = new DesktopKaaPlatformContext();

        // setup state listener
        SimpleKaaClientStateListener stateListener = new SimpleKaaClientStateListener() {
            @Override
            public void onStarted() {
                super.onStarted();

                LOG.info("Kaa client started");

                showConfiguration();
            }

            @Override
            public void onStopped() {
                super.onStopped();

                LOG.info("Kaa client stopped");
            }
        };

        // setup client
        client = Kaa.newClient(
            platformContext,
            stateListener
        );

        // setup profile container
        GarageDoorProfile profile = new GarageDoorProfile("SN12301231", OperatingSystem.Java, "1.4.2");

        // set the profile container
        client.setProfileContainer(() -> profile);

        // persist configuration
        client.setConfigurationStorage(
                new SimpleConfigurationStorage(platformContext, "configuration.cfg")
        );

        // listen for configuration changes
        client.addConfigurationListener(configuration -> {
            LOG.info("Received configuration update");

            showConfiguration();
        });

        // start the client
        client.start();

        switch (mode) {
            case DOOR:
                startDoorMode();
                break;

            case REMOTE:
                startRemoteMode();
                break;

            default:
                return;
        }

        // Stop the Kaa client and release all the resources which were in use.
        client.stop();

        LOG.info("-- garage application finished --");
    }

    private static void startDoorMode() {
        LOG.info("Starting in door mode");

        // show some help information
        LOG.info("The following commands are available:");
        LOG.info("> exit - exits the application");

        boolean isRunning = true;

        // get user input
        while (isRunning){
            String userInput = getUserInput();

            switch(userInput){
                case "exit":
                    isRunning = false;
                    break;

                default:
                    LOG.warn("invalid command '" + userInput + "' requested");
            }
        }
    }

    private static void startRemoteMode() {
        LOG.info("Starting in remote mode");

        // show some help information
        LOG.info("The following commands are available:");
        LOG.info("> open - opens the garage door");
        LOG.info("> close - closes the garage door");
        LOG.info("> exit - exits the application");

        boolean isRunning = true;

        // get user input
        while (isRunning){
            String userInput = getUserInput();

            switch(userInput){
                case "open":
                    openDoor();
                    break;

                case "close":
                    closeDoor();
                    break;

                case "exit":
                    isRunning = false;
                    break;

                default:
                    LOG.warn("invalid command '" + userInput + "' requested");
            }
        }
    }

    private static void openDoor() {
        LOG.info("opening the door");
    }

    private static void closeDoor() {
        LOG.info("closing the door");
    }

    private static void showConfiguration() {
        GarageDoorConfiguration configuration = client.getConfiguration();

        int speed = configuration.getSpeed();

        LOG.info("Configured speed: " + speed);
    }

    private static String getUserInput() {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String userInput = null;

        try {
            userInput = br.readLine();
        } catch (IOException e) {
            LOG.error("IOException has occurred: " + e.getMessage());
        }

        return userInput;
    }
}
