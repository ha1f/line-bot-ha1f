package net.ha1f;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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

import retrofit2.Call;

@SpringBootApplication
@LineMessageHandler
public class LinebotApplication {

    private static final Pattern NOCONTENT_PATTERN = Pattern.compile("^[?？!！…・。、,.〜ーｗw笑 ]+$");
    private static final Pattern SUFFIX_MARK = Pattern.compile("(やなぁ|だよ|やろ|やん|やんけ|[?？!！。、,.〜ーｗw笑])+$");

    private static final List<String> GOODBYE_SUFFIXS = ImmutableList.of("退出",
                                                                         "退出して",
                                                                         "でていって",
                                                                         "出ていって",
                                                                         "退出願います",
                                                                         "ばいばい",
                                                                         "バイバイ",
                                                                         "さよなら",
                                                                         "さよーなら",
                                                                         "さようなら",
                                                                         "またね");
    private static final Pattern HAPPY_INTERJECTION = Pattern.compile(
            "^((わーい|いぇい|やった|いえーい)+|嬉しい|うれしい|うれちい|うれち|うれし|最高|幸せ|しあわせ|優しい|やさしい)+$");

    private static final Map<String, String> NP = ImmutableMap.of(
            "嫌い", "好き",
            "ぶさいく", "かわいい",
            "ぶす", "かわいい"
    );

    private static final Map<String, List<String>> GREETINGS = ImmutableMap.of(
            "おはよう", ImmutableList.of("おはよう"),
            "おやすみ", ImmutableList.of("おやすみ"),
            "よろしくね", ImmutableList.of("こちらこそ"),
            "はるふ", ImmutableList.of("はるふだよ")
    );

    @Autowired
    private LineMessagingService lineMessagingService;

    public static void main(String[] args) {
        SpringApplication.run(LinebotApplication.class, args);
    }

    private static void logEvent(Event event) {
        System.out.println("event received");
    }

    private static void logResponse(BotApiResponse response) {
        System.out.println("message sent");
    }

    private Call<BotApiResponse> leaveRequest(Source source) throws Exception {
        if (source instanceof GroupSource) {
            return lineMessagingService.leaveGroup(((GroupSource) source).getGroupId());
        }
        if (source instanceof RoomSource) {
            return lineMessagingService.leaveRoom(((RoomSource) source).getRoomId());
        }
        return null;
    }

    private BotApiResponse replyWithMessages(String replyToken, List<Message> messages) throws Exception {
        final BotApiResponse apiResponse = lineMessagingService
                .replyMessage(new ReplyMessage(replyToken, messages))
                .execute().body();
        logResponse(apiResponse);
        return apiResponse;
    }

    private Function<List<Message>, BotApiResponse> getReplier(String replyToken) {
        return (List<Message> messages) -> {
            try {
                return replyWithMessages(replyToken, messages);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static <T> T chooseOne(final List<T> candidates) {
        Random r = new Random(System.currentTimeMillis());
        return candidates.get(r.nextInt(candidates.size()));
    }

    private static String randomized(final String text) {
        return text + chooseOne(ImmutableList.of(
                "！",
                "〜",
                "〜〜",
                "！！",
                "〜！"
        ));
    }

    private BotApiResponse replyTextMessage(MessageEvent<TextMessageContent> event) throws Exception {

        final String senderId = event.getSource().getSenderId();
        final Function<List<Message>, BotApiResponse> replier = getReplier(event.getReplyToken());
        final Function<String, BotApiResponse> singleTextReplier = (String text) ->
                replier.apply(Collections.singletonList(new TextMessage(text)));

        final String originalText = event.getMessage().getText();
        final Boolean isQuestion = ImmutableList.of("?", "？").stream().anyMatch(originalText::endsWith);
        String text = SUFFIX_MARK.matcher(originalText).replaceFirst("");
        final Boolean isNoContent = NOCONTENT_PATTERN.matcher(originalText).matches();

        if (isQuestion) {
            text = Pattern.compile("(なの|なん)*(かなあ|かなぁ)+$").matcher(text).replaceFirst("");
        }

        // wwみたいなときはオウム返し
        if (isNoContent || text.isEmpty()) {
            return singleTextReplier.apply(originalText);
        }

        // 退出コマンド
        if (text.contains("はるふ") && GOODBYE_SUFFIXS.stream().anyMatch(text::endsWith)) {
            Call<BotApiResponse> leaveCall = leaveRequest(event.getSource());
            if (leaveCall != null) {
                singleTextReplier.apply(randomized("また遊んでね")); // ignore response
                final BotApiResponse leaveResponse = leaveCall.execute().body();
                logResponse(leaveResponse);
                return leaveResponse;
            } else {
                return singleTextReplier.apply("二人きりの時間を楽しもうな！");
            }
        }

        // 挨拶
        final List<String> greeting = GREETINGS.get(text);
        if (greeting != null) {
            return singleTextReplier.apply(randomized(chooseOne(greeting)));
        }

        // いちゃいちゃ
        if (isQuestion) {
            if (ImmutableList.of("わたし", "私", "ゆかり").stream().anyMatch(text::contains)) {
                if (text.endsWith("好き")) {
                    final List<String> candidates = ImmutableList.of(
                            "好きに決まってるやん？",
                            "決まってるやん？",
                            "わかってるやん？",
                            "好きすぎ"
                    );
                    return singleTextReplier.apply(chooseOne(candidates));
                } else if (text.endsWith("嫌い")) {
                    final List<String> candidates = ImmutableList.of(
                            "なんでそんなこと聞くん？",
                            "そんなわけなくない？？むしろ・・・"
                    );
                    return singleTextReplier.apply(chooseOne(candidates));
                }
            }
        }

        // 独り言に応える
        if (ImmutableList.of("つかれた", "疲れた", "がんばった", "頑張った", "しんどい", "つらい", "ねむい", "眠い").stream()
                         .anyMatch(text::contains)) {
            final List<String> candidates = ImmutableList.of("頑張ったね！お疲れ様！",
                                                             "今度ご飯行こうね",
                                                             "今度あそびに行こうね",
                                                             "いつも頑張ってるの知ってるよ",
                                                             "次あった時ぎゅってしような",
                                                             "頑張りすぎないようにね",
                                                             "大丈夫？おっぱい揉む？"
            );
            return singleTextReplier.apply(chooseOne(candidates));
        }
        if (ImmutableList.of("さみしい", "寂しい").stream().anyMatch(text::contains)) {
            final List<String> candidates = ImmutableList.of("はるふがいる！",
                                                             "元気出して！",
                                                             "今度ご飯行こうな",
                                                             "次あった時ぎゅってしような",
                                                             "今度あそびに行こうね",
                                                             "おいで"
            );
            return singleTextReplier.apply(chooseOne(candidates));
        }

        if (text.endsWith("すごい")) {
            if (isQuestion) {
                return singleTextReplier.apply("すごい！");
            } else {
                return singleTextReplier.apply("すごいね！");
            }
        }

        if (HAPPY_INTERJECTION.matcher(text).matches()) {
            final List<String> candidates = ImmutableList.of("わーい！",
                                                             "やったー！",
                                                             "いぇい！",
                                                             "嬉しい！",
                                                             "うれし〜",
                                                             "あり〜");
            return singleTextReplier.apply(chooseOne(candidates));
        }

        if (ImmutableList.of("ありがとう", "感謝", "thank").stream().anyMatch(text::contains)) {
            final List<String> candidates = ImmutableList.of("いえいえ", "こちらこそ！", "どういたしまして〜😊", "ありがと！");
            return singleTextReplier.apply(chooseOne(candidates));
        }

        if (ImmutableList.of("死にたい", "しにたい").stream().anyMatch(text::contains)) {
            final List<String> candidates = ImmutableList.of("死なないで",
                                                             "元気出して",
                                                             "はるふがいるやで",
                                                             "はるふはいつもそばにいるよ");
            return singleTextReplier.apply(randomized(chooseOne(candidates)));
        }

        // お願いに答える
        if (text.endsWith("ほめて")) {
            final List<String> candidates = ImmutableList.of("すごい！",
                                                             "がんばったね！",
                                                             "お疲れ様！",
                                                             "いつも頑張ってるの知ってるよ！",
                                                             "さすがすぎる！"
            );
            return singleTextReplier.apply(chooseOne(candidates));
        }
        if (ImmutableList.of("あそんで", "遊んで", "あそぼ", "ハグして").stream().anyMatch(text::endsWith)) {
            final List<String> candidates = ImmutableList.of("あそぼ",
                                                             "あそんで",
                                                             "約束やで",
                                                             "ハグしよ"
            );
            return singleTextReplier.apply(randomized(chooseOne(candidates)));
        }

        if (text.contains("はるふ")) {
            if (text.contains("好き")) {
                final List<String> candidates = ImmutableList.of("照れるやん！！",
                                                                 "嬉しい",
                                                                 "好き・・・"
                );
                return singleTextReplier.apply(chooseOne(candidates));
            }
            if (text.contains("ひど")) {
                final List<String> candidates = ImmutableList.of("ごめんね",
                                                                 "ひどいね",
                                                                 "・・・ごめん"
                );
                return singleTextReplier.apply(chooseOne(candidates));
            }
        }

        // その他はやでをつける
        if (text.endsWith("やで")) {
            return singleTextReplier.apply(text);
        } else {
            return singleTextReplier.apply(text + "やで");
        }
    }

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        logEvent(event);
        replyTextMessage(event);
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
