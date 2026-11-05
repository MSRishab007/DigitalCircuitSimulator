package simulator.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfCheckTest {
    @Test
    void everySelfCheckPasses() {
        List<SelfCheck.Result> results = new SelfCheck().run();

        assertFalse(results.isEmpty());
        for (SelfCheck.Result result : results) {
            assertTrue(result.passed, result.name + ": " + result.detail);
        }
    }

    @Test
    void guideIsTheSameContentOnEverySurface() {
        for (UserGuide.Surface surface : UserGuide.Surface.values()) {
            String html = UserGuide.html(surface);
            assertTrue(html.contains("Writing a netlist"));
            assertTrue(html.contains("<code>FUNC f 10 A B Y = A &amp; !B</code>"));
            assertTrue(UserGuide.text(surface).contains("FUNC f 10 A B Y = A & !B"));
        }
    }

    @Test
    void guideDescribesEachSurfacesOwnControls() {
        assertTrue(UserGuide.html(UserGuide.Surface.DESKTOP).contains("Shift+F5"));
        assertTrue(UserGuide.html(UserGuide.Surface.BROWSER).contains("nothing is uploaded"));
        assertEquals(3, SelfCheck.guideNetlistSamples().size());
    }
}
