/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2021 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.retrooper.packetevents.wrapper.play.server;

import com.github.retrooper.packetevents.event.impl.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.data.chat.ClickEvent.ClickType;
import com.github.retrooper.packetevents.protocol.data.chat.Color;
import com.github.retrooper.packetevents.protocol.data.chat.component.TextComponent;
import com.github.retrooper.packetevents.protocol.data.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WrapperPlayServerChatMessage extends PacketWrapper<WrapperPlayServerChatMessage> {
    private static final JSONParser PARSER = new JSONParser();
    private static final int MODERN_MESSAGE_LENGTH = 262144;
    private static final int LEGACY_MESSAGE_LENGTH = 32767;
    private String jsonMessageRaw;
    private List<TextComponent> messageComponents;
    private ChatPosition position;
    private UUID senderUUID;

    public WrapperPlayServerChatMessage(PacketSendEvent event) {
        super(event);
    }

    //TODO Constructor with components

    public WrapperPlayServerChatMessage(String jsonMessageRaw, ChatPosition position) {
        super(PacketType.Play.Server.CHAT_MESSAGE);
        this.jsonMessageRaw = jsonMessageRaw;
        this.position = position;
        this.senderUUID = new UUID(0L, 0L);
    }

    public WrapperPlayServerChatMessage(String jsonMessageRaw, ChatPosition position, UUID senderUUID) {
        super(PacketType.Play.Server.CHAT_MESSAGE);
        this.jsonMessageRaw = jsonMessageRaw;
        this.position = position;
        this.senderUUID = senderUUID;
    }

    @Override
    public void readData() {
        int maxMessageLength = serverVersion.isNewerThanOrEquals(ServerVersion.v_1_13) ? MODERN_MESSAGE_LENGTH : LEGACY_MESSAGE_LENGTH;
        this.jsonMessageRaw = readString(maxMessageLength);

        //Is the server 1.8+ or is the client 1.8+? 1.7.10 servers support 1.8 clients, and send the chat position.
        if (serverVersion.isNewerThanOrEquals(ServerVersion.v_1_8) || clientVersion.isNewerThanOrEquals(ClientVersion.v_1_8)) {
            byte positionIndex = readByte();
            position = ChatPosition.VALUES[positionIndex];
        } else {
            //Always chat in 1.7.10 protocol.
            position = ChatPosition.CHAT;
        }

        if (serverVersion.isNewerThanOrEquals(ServerVersion.v_1_16)) {
            this.senderUUID = readUUID();
        } else {
            this.senderUUID = new UUID(0L, 0L);
        }

        //Parse json message
        JSONObject fullJsonObject = null;
        try {
            fullJsonObject = (JSONObject) PARSER.parse(jsonMessageRaw);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        List<JSONObject> jsonObjects = new ArrayList<>();
        //We add the whole JSON object as it contains the data for the parent component
        jsonObjects.add(fullJsonObject);
        //Extra components, I'm not sure why minecraft designed their component system like this (parent and extra components)
        //Everything could have just been one array of components, no parent required
        JSONArray jsonArray = (JSONArray) fullJsonObject.getOrDefault("extra", new JSONArray());
        for (Object o : jsonArray) {
            jsonObjects.add((JSONObject) o);
        }
        messageComponents = new ArrayList<>();

        for (JSONObject jsonObject : jsonObjects) {
            TextComponent.Builder builder = TextComponent.builder();
            //Read general data in this message component

            String text = (String) jsonObject.getOrDefault("text", "");
            String color = (String) jsonObject.getOrDefault("color", "");
            String font = (String) jsonObject.getOrDefault("font", "");
            String insertion = (String) jsonObject.getOrDefault("insertion", "");
            boolean bold = (boolean) jsonObject.getOrDefault("bold", false);
            boolean italic = (boolean) jsonObject.getOrDefault("italic", false);
            boolean underlined = (boolean) jsonObject.getOrDefault("underlined", false);
            boolean strikeThrough = (boolean) jsonObject.getOrDefault("strikethrough", false);
            boolean obfuscated = (boolean) jsonObject.getOrDefault("obfuscated", false);
            builder = builder
                    .text(text).color(Color.getByName(color))
                    .font(font).insertion(insertion)
                    .bold(bold).italic(italic).underlined(underlined)
                    .strikeThrough(strikeThrough).obfuscated(obfuscated);

            //Read click events
            JSONObject clickEvents = (JSONObject) jsonObject.get("clickEvent");
            if (clickEvents != null) {
                //Read all click events
                String openURLValue = (String) clickEvents.getOrDefault(ClickType.OPEN_URL.getName(), "");
                String openFileValue = (String) clickEvents.getOrDefault(ClickType.OPEN_FILE.getName(), "");
                String runCommandValue = (String) clickEvents.getOrDefault(ClickType.RUN_COMMAND.getName(), "");
                String suggestCommandValue = (String) clickEvents.getOrDefault(ClickType.SUGGEST_COMMAND.getName(), "");
                String changePageValue = (String) clickEvents.getOrDefault(ClickType.CHANGE_PAGE.getName(), "");
                String copyToClipboardValue = (String) clickEvents.getOrDefault(ClickType.COPY_TO_CLIPBOARD.getName(), "");
                //Add them to the builder
                builder = builder.openURLClickEvent(openURLValue).openFileClickEvent(openFileValue)
                        .runCommandClickEvent(runCommandValue).suggestCommandClickEvent(suggestCommandValue)
                        .changePageClickEvent(changePageValue).copyToClipboardClickEvent(copyToClipboardValue);
            }

            //Construct the component
            TextComponent component = builder.build();
            //Add to list of components
            messageComponents.add(component);
        }
    }

    @Override
    public void readData(WrapperPlayServerChatMessage wrapper) {
        this.jsonMessageRaw = wrapper.jsonMessageRaw;
        this.position = wrapper.position;
        this.senderUUID = wrapper.senderUUID;
    }

    @Override
    public void writeData() {
        int maxMessageLength = serverVersion.isNewerThanOrEquals(ServerVersion.v_1_13) ? MODERN_MESSAGE_LENGTH : LEGACY_MESSAGE_LENGTH;
        writeString(jsonMessageRaw, maxMessageLength);

        //Is the server 1.8+ or is the client 1.8+? (1.7.10 servers support 1.8 clients, and send the chat position for 1.8 clients)
        if (serverVersion.isNewerThanOrEquals(ServerVersion.v_1_8) || clientVersion.isNewerThanOrEquals(ClientVersion.v_1_8)) {
            writeByte(position.ordinal());
        }

        if (serverVersion.isNewerThanOrEquals(ServerVersion.v_1_16)) {
            writeUUID(senderUUID);
        }
    }

    public List<TextComponent> getMessageComponents() {
        return messageComponents;
    }

    public void setMessageComponents(List<TextComponent> components) {
        this.messageComponents = components;
    }

    public String getJSONMessageRaw() {
        return jsonMessageRaw;
    }

    public void setJSONMessageRaw(String jsonMessage) {
        this.jsonMessageRaw = jsonMessage;
    }

    public ChatPosition getPosition() {
        return position;
    }

    public void setPosition(ChatPosition position) {
        this.position = position;
    }

    public UUID getSenderUUID() {
        return senderUUID;
    }

    public void setSenderUUID(UUID senderUUID) {
        this.senderUUID = senderUUID;
    }

    public enum ChatPosition {
        CHAT, SYSTEM_MESSAGE, GAME_INFO;

        public static final ChatPosition[] VALUES = values();
    }
}