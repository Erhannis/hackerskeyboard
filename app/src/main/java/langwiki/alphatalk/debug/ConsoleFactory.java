package org.langwiki.alphatalk.debug;

import jline.TerminalFactory;
import jline.console.ConsoleReader;
import org.langwiki.alphatalk.debug.cli.*;
import org.langwiki.alphatalk.debug.cli.util.ArrayHashMultiMap;
import org.langwiki.alphatalk.debug.cli.util.MultiMap;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class contains factory methods for creating consoles of different
 * capabilities.
 */
class ConsoleFactory {
    /**
     * Facade method for operating the Shell allowing specification of auxiliary
     * handlers (i.e. handlers that are to be passed to all subshells).
     *
     * Run the obtained Shell with commandLoop().
     *
     * @param prompt Prompt to be displayed.
     * @param appName The app name string.
     * @param mainHandler Main command handler.
     * @param in Input reader.
     * @param out Output stream.
     * @param err Error output stream.
     * @param promptListener Listener for console prompt. It can be {@code null}.
     *
     * @return Shell that can be either further customized or run directly by calling commandLoop().
     */
    static Shell createConsoleShell(String prompt, String appName, Object mainHandler,
                                    BufferedReader in, PrintStream out, PrintStream err, ConsoleIO.PromptListener promptListener) {
        ConsoleIO io = new ConsoleIO(in, out, err);
        if (promptListener != null) {
            io.setPromptListener(promptListener);
        }

        List<String> path = new ArrayList<String>(1);
        path.add(prompt);

        MultiMap<String, Object> modifAuxHandlers = new ArrayHashMultiMap<String, Object>();
        modifAuxHandlers.put("!", io);

        Shell theShell = new Shell(new Shell.Settings(io, io, modifAuxHandlers, false),
                new CommandTable(new DashJoinedNamer(true)), path);
        theShell.setAppName(appName);

        theShell.addMainHandler(theShell, "!");
        theShell.addMainHandler(new HelpCommandHandler(), "?");
        theShell.addMainHandler(mainHandler, "");

        return theShell;
    }

    /**
     * Facade method for operating the Unix-like terminal supporting line editing and command
     * history.
     *
     * @param prompt Prompt to be displayed
     * @param appName The app name string
     * @param mainHandler Main command handler
     * @param input Input stream.
     * @param output Output stream.
     * @return Shell that can be either further customized or run directly by calling commandLoop().
     */
    static Shell createTerminalConsoleShell(String prompt, String appName,
            ShellCommandHandler mainHandler, InputStream input, OutputStream output) {
        try {
            PrintStream out = new PrintStream(output);

            // Build jline terminal
            jline.Terminal term = TerminalFactory.get();
            final ConsoleReader console = new ConsoleReader(input, output, term);
            console.setBellEnabled(true);
            console.setHistoryEnabled(true);

            // Build console
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new ConsoleReaderInputStream(console)));

            ConsoleIO.PromptListener promptListener = new ConsoleIO.PromptListener() {
                @Override
                public boolean onPrompt(String prompt) {
                    console.setPrompt(prompt);
                    return true; // suppress normal prompt
                }
            };

            return createConsoleShell(prompt, appName, mainHandler, in, out, out, promptListener);
        } catch (Exception e) {
            // Failover: use default shell
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            PrintStream out = new PrintStream(output);

            return createConsoleShell(prompt, appName, mainHandler, in, out, out, null);
        }
    }

    /**
     * Facade method for operating the Telnet Shell supporting line editing and command
     * history over a socket.
     *
     * @param prompt Prompt to be displayed
     * @param appName The app name string
     * @param mainHandler Main command handler
     * @param input Input stream.
     * @param output Output stream.
     * @return Shell that can be either further customized or run directly by calling commandLoop().
     */
    static Shell createTelnetConsoleShell(String prompt, String appName,
            ShellCommandHandler mainHandler, InputStream input, OutputStream output) {
        try {
            // Set up nvt4j; ignore the initial clear & reposition
            final nvt4j.impl.Terminal nvt4jTerminal = new nvt4j.impl.Terminal(input, output) {
                private boolean cleared;
                private boolean moved;

                @Override
                public void clear() throws IOException {
                    if (this.cleared)
                        super.clear();
                    this.cleared = true;
                }

                @Override
                public void move(int row, int col) throws IOException {
                    if (this.moved)
                        super.move(row, col);
                    this.moved = true;
                }
            };
            nvt4jTerminal.put(nvt4j.impl.Terminal.AUTO_WRAP_ON);
            nvt4jTerminal.setCursor(true);

            // Have JLine do input & output through telnet terminal
            final InputStream jlineInput = new InputStream() {
                @Override
                public int read() throws IOException {
                    return nvt4jTerminal.get();
                }
            };
            final OutputStream jlineOutput = new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    nvt4jTerminal.put(value);
                }
            };

            return createTerminalConsoleShell(prompt, appName, mainHandler, jlineInput, jlineOutput);
        } catch (Exception e) {
            // Failover: use default shell
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            PrintStream out = new PrintStream(output);

            return createConsoleShell(prompt, appName, mainHandler, in, out, out, null);
        }
    }
}

/**
 * InputStream wrapper for jline.ConsoleReader
 */
class ConsoleReaderInputStream extends InputStream {
    private final ConsoleReader reader;
    private String line = null;
    private int index = 0;
    private boolean eol = false;

    public ConsoleReaderInputStream(final ConsoleReader reader) {
        this.reader = reader;
    }

    public int read() throws IOException {
        if (eol) {
            eol = false;
            return -1; // (temporary) end of stream
        }

        if (line == null) {
            try {
                line = reader.readLine();
            } catch (IOException e) {
                return -1;
            }
        }

        if (line == null) {
            return -1;
        }

        if (index >= line.length()) {
            index = 0;
            line = null;
            eol = true;
            return '\n'; // lines are ended with a newline
        }

        return line.charAt(index++);
    }
}