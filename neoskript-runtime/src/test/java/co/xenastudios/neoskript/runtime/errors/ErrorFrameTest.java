/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.errors.ErrorSource.Location;
import co.xenastudios.neoskript.runtime.errors.RuntimeError.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Skript's frame limits, and its messages for errors and the end of a frame. */
class ErrorFrameTest {

    private static RuntimeError error(String script, int line) {
        return new RuntimeError(Level.ERROR, new ErrorSource("effect", "Broadcast", line, "broadcast x", script), "bad",
                null);
    }

    /** Which of {@code count} errors from one line are printed. */
    private static List<Boolean> printed(ErrorFrame frame, int line, int count) {
        List<Boolean> printed = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            printed.add(frame.add(error("a.sk", line)));
        }
        return printed;
    }

    @Test
    void aLinePrintsTwoPerFrameAndIsTimedOutAtFour() {
        ErrorFrame frame = new ErrorFrame(ErrorFrame.Limits.DEFAULT);
        assertEquals(List.of(true, true, false, false, false), printed(frame, 1, 5));
        ErrorFrame.Output output = frame.advance();
        assertEquals(Map.of(new Location("a.sk", 1), 5), output.totalErrors());
        assertEquals(Map.of(new Location("a.sk", 1), 3), output.skippedErrors());
        assertEquals(Set.of(new Location("a.sk", 1)), output.newTimeouts());
        // Timed out for 10 frames: nothing from the line is printed, and the timeout isn't new any more.
        for (int i = 0; i < 10; i++) {
            assertEquals(List.of(false), printed(frame, 1, 1));
            ErrorFrame.Output later = frame.advance();
            assertEquals(Set.of(), later.newTimeouts());
            assertEquals(Map.of(new Location("a.sk", 1), 1), later.skippedErrors());
        }
        assertEquals(List.of(true, true, false), printed(frame, 1, 3));
    }

    @Test
    void aFramePrintsEightInAll() {
        ErrorFrame frame = new ErrorFrame(ErrorFrame.Limits.DEFAULT);
        int shown = 0;
        for (int line = 1; line <= 10; line++) {
            if (frame.add(error("a.sk", line))) {
                shown++;
            }
        }
        assertEquals(8, shown);
        ErrorFrame.Output output = frame.advance();
        assertEquals(2, output.skippedErrors().size());
        assertEquals(Set.of(), output.newTimeouts());
        assertTrue(frame.add(error("a.sk", 10)));
    }

    @Test
    void threeErrorsFromALineDontTimeItOut() {
        ErrorFrame frame = new ErrorFrame(ErrorFrame.Limits.DEFAULT);
        printed(frame, 1, 3);
        assertEquals(Set.of(), frame.advance().newTimeouts());
        assertTrue(frame.add(error("a.sk", 1)));
    }

    @Test
    void errorsAreFormattedAsSkriptPrintsThem() {
        RuntimeError error = new RuntimeError(Level.ERROR,
                new ErrorSource("expression", "Sign Text", 3, "set {_x} to line 5 of block", "signs.sk"),
                "Signs only have lines from 1 to 4, but tried to obtain line 5", "line 5");
        assertEquals("<light red>The script '<gray>signs.sk<light red>' encountered an error while executing the"
                + " '<gray>Sign Text<light red>' expression<light red>:\n"
                + "<light red>    Signs only have lines from 1 to 4, but tried to obtain line 5<reset>\n"
                + "<gold>    Line 3<white>: <gray>set {_x} to §f§nline 5§7 of block\n \n", RuntimeErrorMessages.error(error));
        RuntimeError warning = new RuntimeError(Level.WARNING, ErrorSource.unknown("effect", "Wake And Sleep"),
                "careful", null);
        assertEquals("<yellow>The script '<gray>-unknown-<yellow>' encountered a warning while executing the"
                + " '<gray>Wake And Sleep<yellow>' effect<yellow>:\n<yellow>    careful<reset>\n"
                + "<gold>    Line 0<white>: <gray>-unknown-\n \n", RuntimeErrorMessages.error(warning));
    }

    @Test
    void theEndOfAFrameSaysWhatWasSkipped() {
        ErrorFrame frame = new ErrorFrame(ErrorFrame.Limits.DEFAULT);
        printed(frame, 4, 4);
        frame.add(error("b.sk", 1));
        ErrorFrame.Output output = frame.advance();
        assertEquals("<gold>2<light red> runtime error[s] were thrown but not printed in the last frame. From:"
                + " <gray>'a.sk' line 4 (2)", RuntimeErrorMessages.skipped(output, Level.ERROR));
        assertEquals("<gold>Line 4<light red> of script '<gray>a.sk<light red>' produced too many runtime errors"
                + " (<gray>4<light red> allowed per frame). No errors from this line will be printed for 10 frames.",
                RuntimeErrorMessages.timedOut(new Location("a.sk", 4), output.limits(), Level.ERROR));
        assertEquals("<light red>Script '<gray>a.sk<light red>' and 1 others produced runtime errors. Please check"
                + " console logs for details.", RuntimeErrorMessages.notification(output, Level.ERROR));
        ErrorFrame.Output quiet = frame.advance();
        assertNull(RuntimeErrorMessages.skipped(quiet, Level.ERROR));
        assertNull(RuntimeErrorMessages.notification(quiet, Level.ERROR));
        assertFalse(quiet.newTimeouts().contains(new Location("b.sk", 1)));
    }
}
