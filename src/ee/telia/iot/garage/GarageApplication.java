package ee.telia.iot.garage;

import org.kaaproject.kaa.client.DesktopKaaPlatformContext;
import org.kaaproject.kaa.client.Kaa;
import org.kaaproject.kaa.client.KaaClient;
import org.kaaproject.kaa.client.SimpleKaaClientStateListener;
import org.kaaproject.kaa.client.configuration.base.SimpleConfigurationStorage;
import org.kaaproject.kaa.client.notification.NotificationListener;
import org.kaaproject.kaa.client.notification.NotificationTopicListListener;
import org.kaaproject.kaa.client.notification.UnavailableTopicException;
import org.kaaproject.kaa.common.endpoint.gen.SubscriptionType;
import org.kaaproject.kaa.common.endpoint.gen.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GarageApplication {
    private static final Logger LOG = LoggerFactory.getLogger(GarageApplication.class);

    private static KaaClient client;
    private static DesktopKaaPlatformContext platformContext;
    private enum Mode {
        INVALID,
        DOOR,
        REMOTE
    };

    private static class BasicNotificationTopicListListener implements NotificationTopicListListener {
        @Override
        public void onListUpdated(List<Topic> list) {
            LOG.info("Topic list was updated");

            showTopicList(list);

            try {
                // try to subscribe to all new optional topics, if any
                List<Long> optionalTopics = extractOptionalTopicIds(list);

                for(Long optionalTopicId : optionalTopics){
                    LOG.info("Subscribing to optional topic {}", optionalTopicId);
                }

                // subscribe to the topic
                client.subscribeToTopics(optionalTopics, true);
            } catch (UnavailableTopicException e) {
                LOG.error("Topic is unavailable, can't subscribe: {}", e.getMessage());
            }
        }
    }

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

        // setup notification topic list listener
        NotificationTopicListListener topicListListener = new BasicNotificationTopicListListener();
        client.addTopicListListener(topicListListener);

        // listen for notifications
        client.addNotificationListener(new NotificationListener() {
            @Override
            public void onNotification(long topicId, GarageDoorStateChangeNotification notification) {
                LOG.info("Notification for topic id [{}] received.", topicId);

                boolean isOpen = notification.getIsOpen();
                boolean isOpening = notification.getIsOpening();
                boolean isClosing = notification.getIsClosing();

                LOG.info(
                    "State updated "
                    + "isOpen=" + (isOpen ? "yes" : "no") + ", "
                    + "isOpening=" + (isOpening ? "yes" : "no") + ", "
                    + "isClosing=" + (isClosing ? "yes" : "no") + ", "
                );
            }
        });

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

        // display list of notification topics
        List<Topic> topicList = client.getTopics();
        showTopicList(topicList);

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

        // remove the listener
        client.removeTopicListListener(topicListListener);

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

    private static List<Long> extractOptionalTopicIds(List<Topic> list) {
        List<Long> topicIds = new ArrayList<>();
        for (Topic t : list) {
            if (t.getSubscriptionType() == SubscriptionType.OPTIONAL_SUBSCRIPTION) {
                topicIds.add(t.getId());
            }
        }
        return topicIds;
    }

    private static void showTopicList(List<Topic> topics) {
        if (topics == null || topics.isEmpty()) {
            LOG.info("Topic list is empty");
        } else {
            for (Topic topic : topics) {
                LOG.info("Topic id: {}, name: {}, type: {}", topic.getId(), topic.getName(), topic.getSubscriptionType());
            }
        }
    }
}
