package co.xenastudios.neoskript.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlatformInfoTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void detectsAPlainNonFoliaServer() {
        PlatformInfo platform = PlatformInfo.detect();
        assertFalse(platform.isFolia());
        assertNotNull(platform.describe());
        assertFalse(platform.describe().isBlank());
    }
}
