<<<<<<< HEAD
# 📘 E2EE Chat – End-to-End Encrypted TCP Chat Program
**Java 21 · Spring Boot 3 · Docker Compose · ECC(X25519) + AES-GCM · TCP 기반 중계 서버**

## 📌 Overview
이 프로젝트는 TCP 기반 End-to-End Encryption 채팅 프로그램을 구현한 과제 프로젝트입니다. 사용자는 CLI 기반 클라이언트를 통해 상대방과 안전하게 채팅하며, 메시지는 서버를 거치지만 서버는 절대 복호화할 수 없습니다. 모든 암호화/복호화는 클라이언트에서 수행됩니다.

## 🧩 System Architecture

+----------------------+ +----------------------+ +----------------------+
| Client #1 | <--> | Chat Server | <--> | Client #2 |
| (Encrypt / Decrypt) | | (Relay only) | | (Encrypt / Decrypt) |
+----------------------+ +----------------------+ +----------------------+

- 서버: Spring Boot 기반 TCP 소켓 서버(9000), 메시지 중계만 수행, 암호문을 history.json에 저장
- 클라이언트: Java 21 CLI, X25519 키교환, AES-GCM 암호화, HISTORY 로컬 저장 지원

## 🔐 End-to-End Encryption Flow
### 1) 클라이언트 첫 로그인
- KeyVault가 X25519 개인키/공개키 생성 및 로컬 저장
- 서버로 ID, PW, PublicKey 전송하여 로그인 또는 회원가입

### 2) 키 교환
사용자 입력: `/key 상대ID`
과정:
1. 클라이언트 → 서버: KEY_REQ
2. 서버 → 요청자: 상대 공개키(KEY_RES)
3. 클라이언트: ECDH 수행 → sharedSecret 생성
4. HKDF-SHA256으로 AES-GCM 세션키 생성
5. 세션키 map에 저장
6. 세션 생성 후 즉시 이전 기록 불러오기

## 🔑 Crypto Spec
| 영역 | 기술 |
|------|------|
| 공개키 암호 | X25519 (ECDH) |
| 키생성 | HKDF-SHA256 |
| 대칭키 | AES-256-GCM |
| 키저장 | PBKDF2 + AES-GCM (local KeyVault) |
| 메시지 포맷 | JSON |

## 🐳 Docker Environment
구성:
- chat-server
- client1
- client2  
  모두 동일 도커 네트워크에서 TCP로 통신

## 📁 Project Structure
e2ee-chat/
├── client/
│ ├── Dockerfile
│ ├── build.gradle
│ └── src/main/java/com/e2ee/client/...
├── server/
│ ├── Dockerfile
│ ├── build.gradle
│ └── src/main/java/com/e2ee/server/...
├── docker-compose.yml
└── README.md

본 프로젝트는 Docker 기반으로 실행되며, 클라이언트 입력은 반드시 docker attach 명령을 통해 개별 컨테이너에 접속하여 수행합니다.

1) 제공된 server.tar / client.tar Docker 이미지를 로드합니다.
2) docker compose up -d 로 백그라운드 실행합니다.
3) client1, client2 컨테이너에 각각 docker attach 로 접속하여 CLI 입력을 수행합니다.
4) Ctrl+P, Ctrl+Q 로 컨테이너를 종료하지 않고 빠져나올 수 있습니다.

## 🚀 Docker 실행 방법
1) Docker 이미지 로드

(제공된 tar 파일을 프로젝트 루트에서 로드합니다)

docker load -i server.tar
docker load -i client.tar

2) Docker Compose 백그라운드 실행

(로그창에서 입력이 불가능하므로 반드시 -d 사용)

docker compose up -d

3) 각 클라이언트 접속
docker attach client1
docker attach client2

4) 클라이언트 종료하지 않고 빠져나오기

(컨테이너는 계속 실행됨)
Ctrl + P , Ctrl + Q

## 🧪 Test Scenario
### ✔ 회원가입
client1:
1
아이디 입력: alice
비밀번호 입력: 1234

client2:
1
아이디 입력: bob
비밀번호 입력: 5678

### ✔ 키교환
client1:
/key bob

client2:
/key alice

### ✔ 메시지 전송
client1:

client2 출력:

### ✔ 서버에 저장되는 암호문 예시

{
"sender": "alice",
"receiver": "bob",
"body": "AES/GCM/NoPadding:IV:Ciphertext",
"timestamp": "2025-12-01T01:23:00"
}

## 📦 Data Persistence
### 서버 저장
server/data/users.json
server/data/history.json

### 클라이언트 저장
~/.e2ee-chat/keystore/
~/.e2ee-chat/history/
