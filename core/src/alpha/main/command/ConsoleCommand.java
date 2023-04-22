package alpha.main.command;

import java.util.LinkedList;
import java.util.List;

import alpha.main.command.ConsoleCommandOptionData.OptionType;

public abstract class ConsoleCommand {

    private String name = new String();
    private String description = new String();
    private LinkedList<ConsoleCommandOptionData> options = new LinkedList<>();

    public ConsoleCommand(String name) {
        this.name = name;
    }

    public ConsoleCommand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getHelpString() {
        StringBuilder builder = new StringBuilder();
        for (ConsoleCommandOptionData option : getOptions()) {
            builder.append(String.format("\n\t%s: %s %s", //
                    option.getName(), //
                    option.getDescription(), //
                    option.isRequired() ? "(<command.required>[Required])" : ""));
        }

        return builder.toString();
    }

    public ConsoleCommand addCommandOption(String name, OptionType type, String description,
            boolean supportAutoComplete,
            boolean isRequired) {
        options.add(new ConsoleCommandOptionData(name, type, description, supportAutoComplete, isRequired));
        return this;
    }

    public ConsoleCommand addCommandOption(String name, OptionType type, String description,
            boolean supportAutoComplete) {
        return addCommandOption(name, type, description, supportAutoComplete, false);
    }

    public ConsoleCommand addCommandOption(String name, OptionType type, String description) {
        return addCommandOption(name, type, description, false, false);
    }

    public List<ConsoleCommandOptionData> getOptions() {
        return options;
    }

    public void runCommand(ConsoleCommandEvent event) {
        onCommand(event);
    }

    public void onAutoComplete(ConsoleAutoCompleteEvent event) {

    }

    public abstract void onCommand(ConsoleCommandEvent event);
}
