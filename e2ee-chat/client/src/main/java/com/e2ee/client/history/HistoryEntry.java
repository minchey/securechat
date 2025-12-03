package com.e2ee.client.history;

/**
 * 하나의 채팅 기록(히스토리) 라인을 담는 DTO.
 * - sender: 메시지를 보낸 사람(내 ID 또는 상대 ID)
 * - receiver: 메시지 받는 사람
 * - message: 평문 메시지
 * - timestamp: 문자열 형태 시간
 *
 * JSON 파일에 배열 형태로 저장된다.
 */
public class HistoryEntry {

    public String sender;
    public String receiver;
    public String message;
    public String timestamp;

    public HistoryEntry() {
    }

    public HistoryEntry(String sender, String receiver, String message, String timestamp) {
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
