package seedu.address.repro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import seedu.address.MainApp;
import seedu.address.commons.util.AppUtil;
import seedu.address.model.person.Person;

/** An isolated observation test of the real MainApp, its window and its command input. */
public class NameDuplicateGuiReproTest {
    private static final String MASTER = "f9067bcc6f7df215dac7249fa9d034b227e41b4a";
    private static final Path EVIDENCE = Path.of("build", "gui-repro-evidence");
    private final StringBuilder transcript = new StringBuilder();
    private Stage stage;

    @Test
    @Timeout(90)
    public void runningMaster_acceptsCaseAndInternalSpaceDuplicates() throws Exception {
        Files.createDirectories(EVIDENCE);
        Path data = AppUtil.getApplicationDirectory().resolve("data/clientbook.json");
        assertFalse(Files.exists(data), "Use a fresh disposable app data directory");
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        });
        started.get(15, TimeUnit.SECONDS);
        MainApp app = new MainApp();
        app.init();
        try {
            stage = fx(() -> {
                Stage window = new Stage();
                app.start(window);
                assertTrue(window.isShowing(), "The actual ClientBook window must be running");
                window.getScene().getRoot().applyCss();
                window.getScene().getRoot().layout();
                return window;
            });
            assertEquals(List.of(), displayedNames());
            String baseline = submit("add r/Client n/John Doe e/john.original@example.com p/83333333");
            assertTrue(baseline.contains("New person added:"), baseline);
            assertEquals(List.of("John Doe"), displayedNames());
            screenshot("01-original-contact.png");

            String exact = submit("add r/Client n/John Doe e/jd-control@example.com p/84444444");
            assertTrue(exact.contains("A contact with this name already exists."), exact);
            assertEquals(List.of("John Doe"), displayedNames());

            String caseVariant = submit("add r/Client n/JOHN DOE e/jd1@example.com p/81111111");
            assertTrue(caseVariant.contains("New person added:"), caseVariant);
            assertEquals(List.of("John Doe", "JOHN DOE"), displayedNames());
            screenshot("02-letter-case-bypass.png");

            String spacingVariant = submit("add r/Client n/John  Doe e/jd2@example.com p/82222222");
            assertTrue(spacingVariant.contains("New person added:"), spacingVariant);
            assertEquals(List.of("John Doe", "JOHN DOE", "John  Doe"), displayedNames());
            screenshot("03-internal-space-bypass.png");
            submit("list");
            assertEquals(List.of("John Doe", "JOHN DOE", "John  Doe"), displayedNames());
            Files.copy(data, EVIDENCE.resolve("clientbook-after.json"), StandardCopyOption.REPLACE_EXISTING);
            transcript.append("\nREPRODUCED_BOTH_DUPLICATE_NAME_BYPASSES=true\n")
                    .append("Product master: ").append(MASTER).append("\n")
                    .append("Java runtime: ").append(System.getProperty("java.version")).append("\n")
                    .append("Displayed names: ").append(displayedNames()).append("\n")
                    .append("Data file: ").append(data).append("\n");
            System.out.println(transcript);
        } finally {
            Files.writeString(EVIDENCE.resolve("gui-transcript.txt"), transcript.toString());
            fx(() -> {
                app.stop();
                if (stage != null) {
                    stage.close();
                }
                return null;
            });
        }
    }

    private String submit(String command) throws Exception {
        String feedback = fx(() -> {
            TextField input = (TextField) stage.getScene().lookup("#commandTextField");
            TextArea result = (TextArea) stage.getScene().lookup("#resultDisplay");
            assertTrue(input != null && result != null, "Use the app's real command box and feedback area");
            input.setText(command);
            input.fireEvent(new ActionEvent(input, input));
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return result.getText();
        });
        transcript.append("\nINPUT: ").append(command).append("\n")
                .append("FEEDBACK: ").append(feedback).append("\n");
        System.out.println("GUI_INPUT: " + command + "\nGUI_FEEDBACK: " + feedback);
        return feedback;
    }

    private List<String> displayedNames() throws Exception {
        return fx(() -> {
            ListView<?> list = (ListView<?>) stage.getScene().lookup("#personListView");
            return list.getItems().stream().map(Person.class::cast)
                    .map(person -> person.getName().fullName).toList();
        });
    }

    private void screenshot(String filename) throws Exception {
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            WritableImage image = stage.getScene().snapshot(null);
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            assertTrue(ImageIO.write(png, "png", EVIDENCE.resolve(filename).toFile()));
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                done.complete(action.call());
            } catch (Throwable error) {
                done.completeExceptionally(error);
            }
        });
        return done.get(20, TimeUnit.SECONDS);
    }
}
