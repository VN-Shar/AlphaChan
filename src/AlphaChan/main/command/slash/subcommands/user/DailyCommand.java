package AlphaChan.main.command.slash.subcommands.user;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import AlphaChan.main.command.SimpleBotSubcommand;
import AlphaChan.main.data.user.UserData;
import AlphaChan.main.handler.DatabaseHandler;
import AlphaChan.main.handler.UserHandler;
import AlphaChan.main.handler.DatabaseHandler.DATABASE;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.MongoCollection;

import java.text.DateFormat;
import java.util.Date;

public class DailyCommand extends SimpleBotSubcommand {
    public DailyCommand() {
        super("daily", "Điểm danh", true, false);
    }

    @Override
    public String getHelpString() {
        return "Điểm danh mỗi ngày";
    }

    @Override
    public void runCommand(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        if (guild == null)
            throw new IllegalStateException("MEMBER IS NOT EXISTS");

        if (member == null)
            throw new IllegalStateException("MEMBER IS NOT EXISTS");

        if (!DatabaseHandler.collectionExists(DATABASE.DAILY, guild.getId()))
            DatabaseHandler.createCollection(DATABASE.DAILY, guild.getId());

        MongoCollection<Document> collection = DatabaseHandler.getDatabase(DATABASE.DAILY).getCollection(guild.getId());

        Bson filter = new Document().append("userId", member.getId());
        Document data = collection.find(filter).limit(1).first();
        UserData userData = UserHandler.getUserAwait(member);

        int money = 0;
        if (data == null || data.isEmpty()) {
            money = userData._addMoney(userData._getLevelCap());
            collection.insertOne(
                    new Document().append("userId", userData.userId).append("time", System.currentTimeMillis()));

        } else {
            if (data.containsKey("time")) {
                Long time = (Long) data.get("time");
                if (System.currentTimeMillis() - time >= 86400000l) { // 1 Day
                    money = userData._addMoney(userData._getLevelCap());
                    collection.replaceOne(filter, new Document().append("userId", userData.userId).append("time",
                            System.currentTimeMillis()));
                }
            }
        }
        if (money > 0)
            reply(event,
                    "📝Điểm dành thanh công\n💰Điểm nhận được: " + money + " Alpha\n💰Điểm hiện tại: " + userData.money,
                    30);
        else {
            if (data != null)
                if (data.containsKey("time")) {
                    Long lastTime = ((Long) data.get("time"));
                    Long time = lastTime + 86400000l - System.currentTimeMillis();
                    Long sec = time / 1000;
                    Long minute = sec / 60;
                    Long hour = minute / 60;
                    Date date = new Date(lastTime);
                    reply(event,
                            "📝Còn " + hour % 24 + " giờ " + minute % 60
                                    + " phút nữa mới có thể điểm danh\n📝Lần điểm danh cuối: "
                                    + DateFormat.getInstance().format(date),
                            30);
                }
        }
    }

}
