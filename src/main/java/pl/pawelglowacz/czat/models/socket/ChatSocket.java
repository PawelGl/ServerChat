package pl.pawelglowacz.czat.models.socket;

import com.google.gson.reflect.TypeToken;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.pawelglowacz.czat.models.MessageFactory;
import pl.pawelglowacz.czat.models.UserModel;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

@EnableWebSocket
@Configuration
public class ChatSocket extends TextWebSocketHandler implements WebSocketConfigurer {

    Map<String, UserModel> userList = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry.addHandler(this, "/chat")
                .setAllowedOrigins("*");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        userList.put(session.getId(), new UserModel(session));
    }


  private  boolean check=false;
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        UserModel userModel = userList.get(session.getId());

        Type factory =new TypeToken<MessageFactory>() {}.getType();

        MessageFactory factoryCreated = MessageFactory.GSON.fromJson(message.getPayload(),factory);

         MessageFactory  factoryNewMessage;
        switch(factoryCreated.getMessageType()){
            case SEND_MESSAGE:
                //pod message znajduje sie normalna w swiecie wiadomsoc
                    factoryNewMessage=new MessageFactory();
                    factoryNewMessage.setMessage(userModel.getNick()+ ": " +factoryCreated.getMessage());
                    factoryNewMessage.setMessageType(MessageFactory.MessageType.SEND_MESSAGE);
                    sendMessageToAll(factoryNewMessage);
                    break;
            case SET_NICK:

                //pod messeg znajduje sie nick uzytkownika
                factoryNewMessage=new MessageFactory();
                if(!isNickFree(factoryCreated.getMessage())){
                    factoryNewMessage.setMessageType(MessageFactory.MessageType.NICK_NOT_FREE);
                    factoryNewMessage.setMessage("Nick jest zajet");
                    sendMessageToUser(userModel,factoryNewMessage);
                    sendJoinPacket(userModel.getNick(),userModel);

                    return;
                }
                sendJoinPacket(factoryCreated.getMessage(),userModel);
                userModel.setNick(factoryCreated.getMessage());
                factoryNewMessage.setMessageType(MessageFactory.MessageType.SEND_MESSAGE);
                factoryNewMessage.setMessage("Ustawiles swoj nick");
                sendMessageToUser(userModel,factoryNewMessage);
                break;

        }
    }

    private boolean isNickFree(String nick){
        for (UserModel userModel : userList.values()) {
            if(userModel.getNick()!=null && nick.equals(userModel.getNick())){
                return false;
            }
        }
        return true;
    }
    public void sendMessageToAllWithoutMe(UserModel model, MessageFactory factory){
        for (UserModel userModel : userList.values()) {
            if(userModel.getSession().getId().equals(model.getSession().getId())){
                continue;
            }
            try {
                userModel.getSession().sendMessage(new TextMessage(convertFactoryToString(factory)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void sendJoinPacket(String nick,UserModel model){
        MessageFactory messageFactory= new MessageFactory();
        messageFactory.setMessageType(MessageFactory.MessageType.USER_JOIN);
        messageFactory.setMessage(nick);
        sendMessageToAll(messageFactory);
    }

    private void sendLeftPacket(String nick,UserModel model) {
        MessageFactory messageFactory = new MessageFactory();
        messageFactory.setMessageType(MessageFactory.MessageType.USER_LEFT);
        messageFactory.setMessage(nick);
        sendMessageToAllWithoutMe(model, messageFactory);
    }

    private String convertFactoryToString(MessageFactory factory){
        return MessageFactory.GSON.toJson(factory);
    }

    public void sendMessageToAll(MessageFactory factory){
        for(UserModel userModel1:userList.values()){
            try {
                userModel1.getSession().sendMessage(new TextMessage(convertFactoryToString(factory)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessageToUser(UserModel userModel,MessageFactory factory){
        try {
            userModel.getSession().sendMessage(new TextMessage(convertFactoryToString(factory)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UserModel usermodel =userList.get(session.getId());
        sendLeftPacket(usermodel.getNick(),usermodel);
        userList.remove(session.getId());
    }
}
