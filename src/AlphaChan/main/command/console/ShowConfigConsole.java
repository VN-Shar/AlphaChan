package AlphaChan.main.command.console;

import AlphaChan.BotConfig;
import AlphaChan.main.command.ConsoleCommandEvent;
import AlphaChan.main.command.ConsoleCommand;
import AlphaChan.main.util.Log;
import AlphaChan.main.util.StringUtils;

public class ShowConfigConsole extends ConsoleCommand {

    public ShowConfigConsole() {
        super("show-config", "\n\t- <> Show bot configures");
    }

    @Override
    public void runCommand(ConsoleCommandEvent command) {
        Log.system("\nBot config: " + StringUtils.mapToLines(BotConfig.getProperties()));

    }
}
