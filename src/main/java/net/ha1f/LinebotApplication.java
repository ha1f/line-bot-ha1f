package net.ha1f;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import com.linecorp.bot.model.event.message.*;
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

    private static final Pattern SUFFIX_MARK = Pattern.compile("(だよ|やろ|やん|やんけ|[?？!！。、,.〜ーｗw])+$");

    @Autowired
    private LineMessagingService lineMessagingService;

    private static String yukariId = "";
    private static Integer yukariPhase = 0;

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
            return lineMessagingService.leaveGroup(
                    ((GroupSource) source).getGroupId());
        } else if (source instanceof RoomSource) {
            return lineMessagingService.leaveRoom(
                    ((RoomSource) source).getRoomId());
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

    private BotApiResponse replyWithSingleMessage(String replyToken, Message message) throws Exception {
        return replyWithMessages(replyToken, Collections.singletonList(message));
    }

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        logEvent(event);
        Call<BotApiResponse> leaveCall = null;

        final String senderId = event.getSource().getSenderId();
        final String originalText = event.getMessage().getText();
        final Boolean isQuestion = ImmutableList.of("?", "？").stream().anyMatch(originalText::endsWith);
        final String text = SUFFIX_MARK.matcher(originalText).replaceFirst("");
        final String replyToken = event.getReplyToken();

        // ゆかり
        if (text.contains("ゆかり") && !text.endsWith("て言って")) {
            yukariId = senderId;
            yukariPhase = 0;
            System.out.println("yukariId set: " + yukariId);
        }
        // [debug] check yukariId
        if (text.equals("yukariId")) {
            replyWithSingleMessage(replyToken, new TextMessage(yukariId));
            return;
        }
        // [debug] check yukariPhase
        if (text.equals("yukariPhase")) {
            replyWithSingleMessage(replyToken, new TextMessage(yukariPhase.toString()));
            return;
        }
        // [command] increment yukariPhase
        if (text.equals("increment")) {
            yukariPhase += 1;
            replyWithSingleMessage(replyToken, new TextMessage("incremented: " + yukariPhase.toString()));
            return;
        } else if (text.equals("decrement")) {
            yukariPhase -= 1;
            replyWithSingleMessage(replyToken, new TextMessage("decremented: " + yukariPhase.toString()));
            return;
        } else if (text.equals("reset")) {
            yukariPhase = 0;
            replyWithSingleMessage(replyToken, new TextMessage("reset: " + yukariPhase.toString()));
            return;
        }
        // ゆかりによるメッセ
        if (yukariId != null && senderId != null && !yukariId.isEmpty() && senderId.equals(yukariId)) {
            if (yukariPhase == 0) {
                replyWithMessages(replyToken,
                        ImmutableList.of(
                                new TextMessage("仕事お疲れ様！！")
                        ));
            } else if (yukariPhase == 1) {
                replyWithMessages(replyToken,
                        ImmutableList.of(
                                new TextMessage("単刀直入にね！ゆかりが好きなんよ"),
                                new TextMessage("知ってただろうけど！")
                        ));
            } else if (yukariPhase == 2) {
                replyWithMessages(replyToken,
                        ImmutableList.of(
                                new TextMessage("色々壁はあって、少し悩んでたし、遠慮していた部分が多かったけど"),
                                new TextMessage("でも逆にそれが困惑させてたの、本当に阿呆だった！")
                        ));
            }  else if (yukariPhase == 3) {
                replyWithMessages(replyToken,
                        ImmutableList.of(
                                new TextMessage("だから、阿呆は阿呆らしく、自分に正直になると、"),
                                new TextMessage("これからもずっと一緒に旅行行ったりゲームしたりしたい")
                        ));
            }  else if (yukariPhase == 4) {
                replyWithMessages(replyToken,
                        ImmutableList.of(
                                new TextMessage("ので！")
                        ));
            } else {
                return;
            }
            yukariPhase += 1;
            return;
        }


        Message message;
        if (text.isEmpty()) {
            message = new TextMessage("内容なすぎ！");
        } else if (ImmutableList.of("つかれた", "疲れた", "がんばった", "頑張った", "しんどい", "つらい", "ねむい", "眠い").stream()
                                .anyMatch(text::contains)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("頑張ったね！お疲れ様！",
                                                             "今度ご飯行こうね",
                                                             "今度あそびに行こうね",
                                                             "いつも頑張ってるの知ってるよ",
                                                             "次あった時ぎゅってしような",
                                                             "頑張りすぎないようにね",
                                                             "大丈夫？おっぱい揉む？");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("ほめて", "すごい", "でしょ").stream().anyMatch(text::endsWith)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("すごい！",
                                                             "がんばったね！",
                                                             "お疲れ様！",
                                                             "いつも頑張ってるの知ってるよ！",
                                                             "さすがすぎる！");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("わーい", "やった", "いえーい", "いぇい").stream().anyMatch(text::contains)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("わーい！", "やったー！", "いぇい！");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("ありがとう", "感謝", "thank", "うれしい", "嬉しい", "たのしい", "楽しい", "うれちい", "優しい", "やさしい")
                                .stream()
                                .anyMatch(text::contains)) {
            Random r = new Random(System.currentTimeMillis());
            final List<String> candidates = ImmutableList.of("いえいえ", "こちらこそ！", "どういたしまして〜😊", "ありがと！");
            message = new TextMessage(candidates.get(r.nextInt(candidates.size())));
        } else if (ImmutableList.of("死にたい", "しにたい").stream().anyMatch(text::contains)) {
            message = new TextMessage("ありたく「死なないことが大事！」");
        } else if (text.contains("はるふ")
                   && ImmutableList.of("退出", "退出して", "でていって", "出ていって", "退出願います", "ばいばい", "バイバイ", "さよなら",
                                       "さよーなら", "さようなら").stream()
                                   .anyMatch(text::endsWith)) {
            leaveCall = leaveRequest(event.getSource());
            if (leaveCall != null) {
                message = new TextMessage("また遊んでね！");
            } else {
                message = new TextMessage("二人きりの時間を楽しもうな");
            }
        } else {
            if (text.endsWith("やで")) {
                message = new TextMessage(text);
            } else {
                message = new TextMessage(text + "やで");
            }
        }

        replyWithSingleMessage(event.getReplyToken(), message);

        if (leaveCall != null) {
            final BotApiResponse leaveResponse = leaveCall.execute().body();
            logResponse(leaveResponse);

        }
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
