package simulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    private static final String AND_NETLIST = "WIRE A\nWIRE B\nWIRE OUT\nAND g 1 A B OUT\n";
    private static final String OR_NETLIST = "WIRE A\nWIRE B\nWIRE OUT\nOR g 1 A B OUT\n";

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @TempDir
    Path folder;

    @BeforeEach
    void captureOutput() {
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
    }

    @AfterEach
    void restoreOutput() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String write(String name, String content) throws Exception {
        Path file = folder.resolve(name);
        Files.writeString(file, content);
        return file.toString();
    }

    @Test
    void netlistAndOutputWithoutACommandRunsASimulation() throws Exception {
        String netlist = write("a.netlist", AND_NETLIST + "STIMULUS 0 A 1\n");
        Path waveform = folder.resolve("a.vcd");

        assertEquals(0, Main.run(new String[]{netlist, waveform.toString()}));

        assertTrue(Files.exists(waveform));
    }

    @Test
    void savesTheWaveformAsAnImage() throws Exception {
        String netlist = write("a.netlist", AND_NETLIST + "STIMULUS 0 A 1\nSTIMULUS 0 B 1\n");
        Path image = folder.resolve("a.png");

        assertEquals(0, Main.run(new String[]{"waveform-image", netlist, image.toString()}));

        java.awt.image.BufferedImage picture = javax.imageio.ImageIO.read(image.toFile());
        assertTrue(picture.getWidth() >= 1000);
        assertTrue(picture.getHeight() > 100);
    }

    @Test
    void warnsWhenNothingDrivesTheCircuit() throws Exception {
        String netlist = write("a.netlist", AND_NETLIST);

        assertEquals(0, Main.run(new String[]{"simulate", netlist, folder.resolve("a.vcd").toString()}));

        assertTrue(out.toString().contains("every wire stays X"));
    }

    @Test
    void truthTableDetectsWiresWhenNoneAreGiven() throws Exception {
        assertEquals(0, Main.run(new String[]{"truth-table", write("a.netlist", AND_NETLIST)}));

        assertTrue(out.toString().startsWith("A B | OUT"));
    }

    @Test
    void equivalentDetectsWiresAndReportsDifferences() throws Exception {
        String first = write("a.netlist", AND_NETLIST);
        String second = write("b.netlist", OR_NETLIST);

        assertEquals(2, Main.run(new String[]{"equivalent", first, second}));
        assertTrue(out.toString().contains("NOT_EQUIVALENT"));
    }

    @Test
    void missingArgumentsPrintUsage() throws Exception {
        String netlist = write("a.netlist", AND_NETLIST);

        assertEquals(1, Main.run(new String[]{"truth-table", netlist, "A,B"}));
        assertEquals(1, Main.run(new String[]{"equivalent", netlist}));
        assertTrue(err.toString().contains("Usage:"));
    }

    @Test
    void missingFileIsNamedInTheError() {
        assertEquals(1, Main.run(new String[]{"validate", "does-not-exist.netlist"}));

        assertTrue(err.toString().contains("VALIDATE FAILED: File not found: does-not-exist.netlist"));
    }
}
