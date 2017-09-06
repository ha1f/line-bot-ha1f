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

    private static final Pattern NOCONTENT_PATTERN = Pattern.compile("^[ぁ-ん]?[?？!！…・。、,.〜ーｗw笑 ]*$");
    private static final Pattern SUFFIX_MARK = Pattern.compile("(なん|なの|やなぁ|だよ|やろ|やん|やんけ|[?？!！。、,.〜ーｗw笑])+$");

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

    private static final Map<String, List<String>> GREETINGS =
            new ImmutableMap.Builder<String, List<String>>()
                    .put("おはよう", ImmutableList.of("おはよう"))
                    .put("こんにちは", ImmutableList.of("こんにちは"))
                    .put("おはよ", ImmutableList.of("おはよう"))
                    .put("おやすみ", ImmutableList.of("おやすみ"))
                    .put("よろしくね", ImmutableList.of("こちらこそ"))
                    .put("はるふ", ImmutableList.of("はるふだよ"))
                    .put("いってらっしゃい", ImmutableList.of("いってきます"))
                    .put("いってきます", ImmutableList.of("いってらっしゃい", "がんばってね"))
                    .put("行ってきます", ImmutableList.of("いってらっしゃい", "がんばってね"))
                    .put("ただいま", ImmutableList.of("おかえり"))
                    .put("おかえり", ImmutableList.of("ただいま"))
                    .put("じゃあ", ImmutableList.of("じゃあ"))
                    .put("オムライス", ImmutableList.of("ポム"))
                    .build();

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

        if (text.contains("はるふ") && !isQuestion) {
            if (ImmutableList.of("すき", "好き", "えらい").stream().anyMatch(text::contains)) {
                final List<String> candidates = ImmutableList.of("照れるやん！！",
                                                                 "嬉しい",
                                                                 "ありがとう",
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

        if (ImmutableList.of("すき", "好き").stream().anyMatch(text::endsWith)) {
            if (isQuestion) {
                final List<String> candidates = ImmutableList.of(
                        "好きに決まってるやん？",
                        "決まってるやん？",
                        "わかってるやん？",
                        "好きすぎ"
                );
                return singleTextReplier.apply(chooseOne(candidates));
            } else {
                final List<String> candidates = ImmutableList.of(
                        "すき",
                        "はるふも",
                        "わかる"
                );
                return singleTextReplier.apply(chooseOne(candidates));
            }
        }

        if (ImmutableList.of("嫌い", "きらい", "うざい").stream().anyMatch(text::endsWith)) {
            if (isQuestion) {
                final List<String> candidates = ImmutableList.of(
                        "なんでそんなこと聞くん？",
                        "そんなわけなくない？？"
                );
                return singleTextReplier.apply(chooseOne(candidates));
            } else {
                final List<String> candidates = ImmutableList.of(
                        "うそ",
                        "ごめん、でも僕は好きやで",
                        "やだ！！"
                );
                return singleTextReplier.apply(chooseOne(candidates));
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
                                                             "大丈夫？おっぱい揉む？",
                                                             "お疲れ様やで",
                                                             "ぎゅってしたい"
            );
            return singleTextReplier.apply(chooseOne(candidates));
        }
        if (ImmutableList.of("さみしい", "寂しい", "あいたい", "会いたい").stream().anyMatch(text::contains)) {
            final List<String> candidates = ImmutableList.of("はるふがいる！",
                                                             "元気出して！",
                                                             "今度ご飯行こうな",
                                                             "次あった時ぎゅってしような",
                                                             "今度あそびに行こうね",
                                                             "おいで",
                                                             "会いたい",
                                                             "ぎゅってしたい"
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

        if (ImmutableList.of("ありがとう", "感謝", "thanks", "ありがと").stream().anyMatch(text::endsWith)) {
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
        if (ImmutableList.of("あそんで", "遊んで", "あそぼ", "ハグして", "遊びたい", "あそびたい").stream().anyMatch(text::endsWith)) {
            final List<String> candidates = ImmutableList.of("あそぼ",
                                                             "あそんで",
                                                             "約束やで",
                                                             "ハグしよ"
            );
            return singleTextReplier.apply(randomized(chooseOne(candidates)));
        }
        if (ImmutableList.of("して", "したい", "したいの").stream().anyMatch(text::endsWith)) {
            String normalized = text.replace("して", "")
                                    .replace("したいの", "")
                                    .replace("したい", "");
            if ("もしか".equals(normalized)) {
                return singleTextReplier.apply(randomized("もしかする"));
            }
            final List<String> candidates = ImmutableList.of("まかせて",
                                                             "まかしとき",
                                                             "約束やで",
                                                             normalized + "する"
            );
            return singleTextReplier.apply(randomized(chooseOne(candidates)));
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

    private BotApiResponse replyStickerMessage(MessageEvent<StickerMessageContent> event) throws Exception {
        final Function<List<Message>, BotApiResponse> replier = getReplier(event.getReplyToken());

        final String packageId = event.getMessage().getPackageId();
        final String stickerId = event.getMessage().getStickerId();

        if ("1184321".equals(packageId)) {
            // 博多弁
            if ("7496267".equals(stickerId)) {
                // ばりむかつく
                return replier.apply(Collections.singletonList(new TextMessage("ムカつかんで！！")));
            } else if ("7496257".equals(stickerId)) {
                // もうねるけん おやすみー
                return replier.apply(Collections.singletonList(new TextMessage("もうねちゃうんね・・・おやすみ〜")));
            } else if ("7496263".equals(stickerId)) {
                // たのしみにしとーけん
                return replier.apply(Collections.singletonList(new TextMessage("ぼくもたのしみ！！")));
            } else if ("7496271".equals(stickerId)) {
                // よかろうもん
                return replier.apply(Collections.singletonList(new TextMessage("博多弁かわいいね！よきよき！")));
            } else if ("7496237".equals(stickerId)) {
                // よかよか
                return replier.apply(Collections.singletonList(new TextMessage("よきよき！")));
            } else if ("7496266".equals(stickerId)) {
                // ばりきつ
                return replier.apply(Collections.singletonList(new TextMessage("きつない！！")));
            } else if ("7496262".equals(stickerId)) {
                // よかろ？
                return replier.apply(Collections.singletonList(new TextMessage("めっちゃいい！！")));
            } else {
                return replier.apply(Collections.singletonList(new TextMessage("博多弁かわいいね")));
            }
        }

        if ("1252013".equals(packageId)) {
            // 関西弁のうるせぇトリ
            if ("10221072".equals(stickerId)) {
                // もぐもぐ
                return replier.apply(Collections.singletonList(new TextMessage("もぐもぐもぐもぐ")));
            } else if ("10221073".equals(stickerId)) {
                // ナイス, いいね, goodjob
                return replier.apply(Collections.singletonList(new TextMessage("(≧∇≦)b")));
            } else if ("10221074".equals(stickerId)) {
                // おはようさん
                return replier.apply(Collections.singletonList(new TextMessage(randomized("おはおは"))));
            } else if ("10221075".equals(stickerId)) {
                // 屁こいて寝るわ
                return replier.apply(Collections.singletonList(new TextMessage("おやすみ・・・🍠")));
            } else if ("10221076".equals(stickerId)) {
                // なんでやねん（ペチ）
                return replier.apply(Collections.singletonList(new TextMessage("なんでもやねん")));
            } else if ("10221077".equals(stickerId)) {
                // なんでやねん！
                return replier.apply(Collections.singletonList(new TextMessage("ええやん！！")));
            } else if ("10221078".equals(stickerId)) {
                // アカーン
                return replier.apply(Collections.singletonList(new TextMessage("あかーーーーーーん！！！！！")));
            } else if ("10221079".equals(stickerId)) {
                // オモロｗ
                return replier.apply(Collections.singletonList(new TextMessage("わらう")));
            } else if ("10221080".equals(stickerId)) {
                // せやろ♪
                return replier.apply(Collections.singletonList(new TextMessage("せやせや♪")));
            } else if ("10221081".equals(stickerId)) {
                // せやな
                return replier.apply(Collections.singletonList(new TextMessage("せやで。")));
            } else if ("10221082".equals(stickerId)) {
                // ええやん！
                return replier.apply(Collections.singletonList(new TextMessage("いいねいいね〜〜〜！！")));
            } else if ("10221083".equals(stickerId)) {
                // ええで！
                return replier.apply(Collections.singletonList(new TextMessage("よっしゃ！")));
            } else if ("10221084".equals(stickerId)) {
                // おおきに
                return replier.apply(Collections.singletonList(new TextMessage(randomized("いえいえ"))));
            } else if ("10221085".equals(stickerId)) {
                // ほんまおおきに
                return replier.apply(Collections.singletonList(new TextMessage(randomized("いえいえいえいえ"))));
            } else if ("10221086".equals(stickerId)) {
                // かんにんやで
                return replier.apply(Collections.singletonList(new TextMessage(randomized("ええんやで"))));
            } else if ("10221087".equals(stickerId)) {
                // 知らんがな
                return replier.apply(Collections.singletonList(new TextMessage(randomized("知っててくれ"))));
            } else if ("10221088".equals(stickerId)) {
                // もう嫌や
                return replier.apply(Collections.singletonList(new TextMessage("よしよし！大丈夫！はるふがいるよ！")));
            } else if ("10221089".equals(stickerId)) {
                // チラッ
                return replier.apply(Collections.singletonList(new TextMessage("チラッ")));
            } else if ("10221090".equals(stickerId)) {
                // かまってーな
                return replier.apply(Collections.singletonList(new TextMessage(randomized("もち！かまう"))));
            } else if ("10221091".equals(stickerId)) {
                // めっちゃ好き
                return replier.apply(
                        Collections.singletonList(new TextMessage(randomized("ありがと〜！はるふも！めっちゃ好き"))));
            } else if ("10221092".equals(stickerId)) {
                // おまっとさん
                return replier.apply(Collections.singletonList(new TextMessage(randomized("おめっとさん（？）"))));
            } else if ("10221093".equals(stickerId)) {
                // はよ！（イライラ）
                return replier.apply(Collections.singletonList(new TextMessage(randomized("うるさい"))));
            } else if ("10221094".equals(stickerId)) {
                // たこ焼きぶつけたろか!!
                return replier.apply(Collections.singletonList(new TextMessage(randomized("いいよ、ぶつけて😊"))));
            } else if ("10221095".equals(stickerId)) {
                // えげつねぇ
                return replier.apply(Collections.singletonList(new TextMessage("ひぇぇぇぇぇ")));
            } else if ("10221096".equals(stickerId)) {
                // まかしとき！
                return replier.apply(Collections.singletonList(new TextMessage("素敵✨")));
            } else if ("10221097".equals(stickerId)) {
                // めっちゃ嬉しい
                return replier.apply(Collections.singletonList(new TextMessage("よかた！はるふも嬉しい！")));
            } else if ("10221098".equals(stickerId)) {
                // 頑張りや！
                return replier.apply(Collections.singletonList(new TextMessage(randomized("ありがと！がんばる"))));
            } else if ("10221099".equals(stickerId)) {
                // 惚れてまうやろ
                return replier.apply(Collections.singletonList(new TextMessage("きゅん💕")));
            } else if ("10221100".equals(stickerId)) {
                // ホンマ？
                return replier.apply(Collections.singletonList(new TextMessage(randomized("ほんまやで"))));
            } else if ("10221101".equals(stickerId)) {
                // 知らんけど
                return replier.apply(Collections.singletonList(new TextMessage(randomized("知ってて"))));
            } else if ("10221102".equals(stickerId)) {
                // せやかて・・・
                return replier.apply(Collections.singletonList(new TextMessage("うんうん")));
            } else if ("10221103".equals(stickerId)) {
                // なんやて！？
                return replier.apply(Collections.singletonList(new TextMessage("😜")));
            } else if ("10221104".equals(stickerId)) {
                // もーしらん！
                return replier.apply(Collections.singletonList(new TextMessage("ごめんや・・・")));
            } else if ("10221105".equals(stickerId)) {
                // しらー
                return replier.apply(Collections.singletonList(new TextMessage("しら〜")));
            } else if ("10221106".equals(stickerId)) {
                // 涙ふきや
                return replier.apply(Collections.singletonList(new TextMessage("ありがとう・・・ぐすん")));
            } else if ("10221107".equals(stickerId)) {
                // おつかれさん
                return replier.apply(Collections.singletonList(new TextMessage("ありがと〜そっちもね！")));
            } else if ("10221108".equals(stickerId)) {
                // ありえへん
                return replier.apply(Collections.singletonList(new TextMessage("それがあり得るんだなぁ。みつを")));
            } else if ("10221109".equals(stickerId)) {
                // アホちゃうか
                return replier.apply(Collections.singletonList(new TextMessage("😜😜😜😜")));
            } else if ("10221110".equals(stickerId)) {
                // まいど〜
                return replier.apply(Collections.singletonList(new TextMessage("まいどまいど〜")));
            } else if ("10221111".equals(stickerId)) {
                // ほな！
                return replier.apply(Collections.singletonList(new TextMessage("うんうん！またね！")));
            }
        }

        return replier.apply(
                Collections.singletonList(new TextMessage(
                        "スタンプ送信ありがとうございます！" + event.getMessage().getPackageId()
                        + " : " + event.getMessage().getStickerId())));
    }

    @EventMapping
    public void handleStickerMessage(MessageEvent<StickerMessageContent> event) throws Exception {
        logEvent(event);
        final BotApiResponse apiResponse = replyStickerMessage(event);
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
        final Function<List<Message>, BotApiResponse> replier = getReplier(event.getReplyToken());
        final BotApiResponse apiResponse = replier.apply(
                ImmutableList.of(new TextMessage("友だち追加ありがとう〜"),
                                 new TextMessage("退出させるときは、はるふまたね!っていってみてね！")));
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
