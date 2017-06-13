package net.ha1f;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.google.common.collect.ImmutableList;

import com.linecorp.bot.client.LineMessagingService;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.BeaconEvent;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.message.VideoMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

@SpringBootApplication
@LineMessageHandler
public class LinebotApplication {

    private static final Pattern SUFFIX_MARK = Pattern.compile("[?？!！。、,.〜ー]+$");

    @Autowired
    private LineMessagingService lineMessagingService;

    public static void main(String[] args) {
        SpringApplication.run(LinebotApplication.class, args);
    }

    private static void logEvent(Event event) {
        System.out.println("event: " + event);
    }

    private static void logResponse(BotApiResponse response) {
        System.out.println("message sent: " + response);
    }

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        logEvent(event);
        final String text = SUFFIX_MARK.matcher(event.getMessage().getText()).replaceFirst("");
        final Message message;
        if (text.contains("みきてぃ")) {
            message = new TextMessage("みきてぃやん！おはよー！");
        } else if (ImmutableList.of("つかれた", "疲れた", "がんばった", "頑張った", "しんどい", "つらい", "ねむい", "眠い").stream()
                                .anyMatch(
                                        text::contains)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("頑張ったね！お疲れ様！",
                                                             "今度ご飯行こうね",
                                                             "今度あそびに行こうね",
                                                             "いつも頑張ってるの知ってるよ",
                                                             "次あった時ぎゅってしような",
                                                             "頑張りすぎないようにね",
                                                             "大丈夫？おっぱい揉む？");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("ほめて", "すごい", "でしょ").stream().anyMatch(
                text::endsWith)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("すごい！",
                                                             "がんばったね！",
                                                             "お疲れ様！",
                                                             "いつも頑張ってるの知ってるよ！",
                                                             "さすがすぎる！");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("わーい", "やったー").stream().anyMatch(text::contains)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("わーい！", "やったー！", "いぇい！");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("ありがとう", "感謝", "thank").stream().anyMatch(text::contains)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("いえいえ", "こちらこそ！", "どういたしまして〜😊");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("死にたい", "しにたい").stream().anyMatch(text::contains)) {
            message = new TextMessage("ありたく「死なないことが大事！」");
        } else if (text.contains("はるふ")
                   && ImmutableList.of("退出", "退出して", "でていって", "出ていって", "退出願います").stream()
                                   .anyMatch(text::endsWith)) {
            final Source source = event.getSource();
            if (source instanceof GroupSource) {
                message = new TextMessage("また遊んでね！");
                final BotApiResponse apiResponse = lineMessagingService.leaveGroup(
                        ((GroupSource) source).getGroupId()).execute().body();
                logResponse(apiResponse);
            } else if (source instanceof RoomSource) {
                message = new TextMessage("また遊んでね！");
                final BotApiResponse apiResponse = lineMessagingService.leaveRoom(
                        ((RoomSource) source).getRoomId()).execute().body();
                logResponse(apiResponse);
            } else {
                message = new TextMessage("二人きりなのに！");
            }
        } else {
            if (text.endsWith("やで")) {
                message = new TextMessage(text);
            } else {
                message = new TextMessage(text + "やで");
            }
        }
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(event.getReplyToken(), Collections.singletonList(message)))
                .execute().body();
        logResponse(apiResponse);
    }

    @EventMapping
    public void handleStickerMessage(MessageEvent<StickerMessageContent> event) throws Exception {
        logEvent(event);
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(event.getReplyToken(),
                                               Collections.singletonList(new TextMessage(
                                                       "スタンプ送信ありがとうございます！" + event.getMessage().getPackageId()
                                                       + " : " + event.getMessage().getStickerId()))))
                .execute().body();
        System.out.println("Sent messages: " + apiResponse);
    }

    @EventMapping
    public void handleImageMessage(MessageEvent<ImageMessageContent> event) throws Exception {
        logEvent(event);
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(event.getReplyToken(),
                                               Collections.singletonList(new TextMessage("画像送信ありがとうございます！"))))
                .execute().body();
        System.out.println("Sent messages: " + apiResponse);
    }

    @EventMapping
    public void handleVideoMessage(MessageEvent<VideoMessageContent> event) throws Exception {
        logEvent(event);
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(event.getReplyToken(),
                                               Collections.singletonList(new TextMessage("動画送信ありがとうございます！"))))
                .execute().body();
        logResponse(apiResponse);
    }

    @EventMapping
    public void handleAudioMessage(MessageEvent<AudioMessageContent> event) throws Exception {
        logEvent(event);
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(event.getReplyToken(),
                                               Collections.singletonList(new TextMessage("音声送信ありがとうございます！"))))
                .execute().body();
        logResponse(apiResponse);
    }

    @EventMapping
    public void handleFollowEvent(FollowEvent event) throws Exception {
        logEvent(event);
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(event.getReplyToken(),
                                               Collections.singletonList(new TextMessage("友だち追加ありがとう〜"))))
                .execute().body();
        logResponse(apiResponse);
    }

    @EventMapping
    public void handleBeaconEvent(BeaconEvent event) throws Exception {
        logEvent(event);
        Message m1 = new TextMessage("ご来店ありがとうございます！");
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(event.getReplyToken(),
                                               Collections.singletonList(m1)))
                .execute().body();
        logResponse(apiResponse);
    }

    @EventMapping
    public void defaultMessageEvent(Event event) {
        logEvent(event);
    }
}
