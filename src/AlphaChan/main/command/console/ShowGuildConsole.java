package AlphaChan.main.command.console;

import AlphaChan.main.command.ConsoleCommandEvent;
import AlphaChan.main.command.SimpleConsoleCommand;
import AlphaChan.main.data.user.GuildData;
import AlphaChan.main.handler.GuildHandler;
import AlphaChan.main.util.Log;

public class ShowGuildConsole extends SimpleConsoleCommand {

    public ShowGuildConsole() {
        super("show-guild", "\n\t- <>: Show all guild that has been cached" + //
                "\n\t- <guild id>: Show specific guild");
    }

    @Override
    public void runCommand(ConsoleCommandEvent command) {

        if (command.getArgumentCount() == 0) {
            Log.info("GUILD STATUS", "Guild counts: " + GuildHandler.getActiveGuildCount());
            for (GuildData gd : GuildHandler.getGuildCache()) {
                Log.info("GUILD STATUS", gd.toDocument().toJson().toString());
            }
        } else if (command.getArgumentCount() == 1) {
            String guildId = command.nextString();
            try {

                if (guildId == null)
                    throw new IllegalArgumentException();

                GuildData gd = GuildHandler.getGuild(guildId);
                Log.info("GUILD STATUS", "Guild id: " + guildId + //
                        "\n" + gd.toDocument().toString());
            } catch (Exception e) {
                Log.warning("Guild with id " + guildId + " not found");
            }
        } else {
            Log.error("Command require {0,2} arguments ");
        }
    }
}