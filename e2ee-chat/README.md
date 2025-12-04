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


## 🚀 Docker 실행 방법
오프라인 환경에서도 실행할 수 있도록 Docker 이미지가 .tar 파일로 제공됩니다.
GitHub Release에서 다음 두 파일을 다운로드하세요:

server.tar

client.tar

1️⃣ Docker 이미지 로드

다운로드한 .tar 파일을 Docker에 로드합니다:

docker load -i server.tar              
docker load -i client.tar

성공하면 아래와 비슷한 메시지가 출력됩니다:

Loaded image: e2ee-chat-chat-server:latest
Loaded image: e2ee-chat-client1:latest

2️⃣ Docker Compose 실행

프로젝트 루트에서 다음 명령어 실행:

docker compose up

3️⃣ 클라이언트 접속

두 개의 클라이언트 컨테이너가 자동으로 실행되며, 각각 다음 명령으로 접속하여 사용합니다:

docker attach client1 
docker attach client2

🐳 Docker 이미지 구성

Docker Compose는 다음 세 컨테이너를 실행합니다:

chat-server

client1

client2

모두 동일 네트워크에서 TCP(9000) 기반으로 통신합니다.

🔒 데이터 저장 위치
서버 (컨테이너 내부)
/app/data/users.json
/app/data/history.json

클라이언트 (컨테이너 내부)
/root/.e2ee-chat/keystore/
 /root/.e2ee-chat/history/

 
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


