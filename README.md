# Distributed Quiz System

A distributed client/server quiz platform built for the Distributed Programming course, featuring automatic server discovery, a replicated server cluster with primary/backup failover, and a JavaFX desktop client for teachers and students.

## Table of Contents

- [Introduction](#introduction)
- [Project Overview](#project-overview)
- [Architecture](#architecture)
  - [High-Level View](#high-level-view)
  - [Directory Service](#directory-service)
  - [Server Cluster](#server-cluster)
  - [Client Application](#client-application)
  - [Communication Protocol](#communication-protocol)
- [Project Structure](#project-structure)
- [Data Persistence](#data-persistence)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Cloning the Repository](#cloning-the-repository)
  - [Building the Project](#building-the-project)
  - [Running the System](#running-the-system)
  - [Running a Server Cluster](#running-a-server-cluster)
- [Troubleshooting](#troubleshooting)
- [Diagrams](#diagrams)
- [Authors](#authors)
- [License](#license)

## Introduction

This repository contains the practical assignment ("Trabalho Prático") developed for the **Distributed Programming** ("Programação Distribuída") course, part of the Bachelor's degree in Computer Engineering at the **Instituto Superior de Engenharia de Coimbra (ISEC)**, academic year 2025/2026.

The assignment brief and evaluation checklist provided by the teaching staff are kept in the [`docs/`](docs/) folder for reference.

The project was developed by:

- Rui Casaca
- Davi Gama
- Afonso David

## Project Overview

The system implements a quiz/questionnaire service with two kinds of users:

- **Teachers** can register (using a shared registration code), create multiple-choice questions with a defined availability window (start/end date and time), list and filter their own questions (future, active or expired), delete questions that have not yet been answered, and inspect answer statistics once a question has expired.
- **Students** can register, browse and answer questions using a per-question access code while the question is active, and consult their personal answer history together with their overall success rate.

Beyond the application-level functionality, the real focus of the assignment is the distributed systems infrastructure behind it:

- Dynamic **discovery** of the active server through a directory service, so clients never need to know a server's address in advance.
- A **replicated server cluster** with a single primary node and one or more backup nodes kept in sync through periodic, incremental replication.
- **Automatic failover**: if the primary server disappears, the directory promotes another registered server and clients transparently reconnect to it.
- **Session resumption**: a client that loses its TCP connection can reconnect and resume its previous session without forcing the user to log in again.

## Architecture

### High-Level View

The system is composed of three independent, separately-launched processes:

```
                +-------------------+
                |     Directory     |   UDP, well-known port (default 9999)
                |  (server registry |
                |   + election)     |
                +---------+---------+
                          ^
             UDP discovery| / heartbeats
                          v
        +-----------------+------------------+
        |                                     |
+---------------+                     +---------------+
|  Server (A)   |<--- multicast ----->|  Server (B)   |   Cluster of 1..N servers
|   PRIMARY     |   heartbeat + SQL   |   BACKUP      |   (one PRIMARY at a time)
|  SQLite DB    |     replication     |  SQLite DB    |
+-------+-------+                     +---------------+
        ^
        | TCP (client requests / responses)
        v
+---------------+
|  Client(s)    |   JavaFX desktop application
|  (Teacher /   |
|   Student)    |
+---------------+
```

Only the primary server exchanges data with clients over TCP. Backup servers exist purely for redundancy: they keep their local SQLite database up to date and are ready to be promoted to primary by the directory if the current primary stops sending heartbeats.

### Directory Service

Entry point: `pt.isec.directory.MainDirectory` (package `pt.isec.directory`).

The directory is a lightweight UDP service with no persistent storage. Its responsibilities are:

- Listen for UDP datagrams on a single well-known port (`9999` by default).
- Accept two kinds of messages: **server registration/heartbeats** and **client discovery requests**.
- Keep an in-memory registry of currently known servers (`ServerInfo`), tracking when each one was last seen.
- Elect a **primary** server: the first server that registered and is still alive is considered primary (insertion-order election, implemented with a `LinkedHashMap`).
- Answer client discovery requests (`TYPE=LOGIN`) with the address of the current primary server.
- Periodically reap (remove) servers that have not sent a heartbeat within a configurable TTL (17 seconds by default), automatically promoting the next server in line to primary.
- Log periodic metrics about the number of known servers and the current primary.

Internally, the directory uses a small thread pool: a UDP listener thread that enqueues incoming datagrams, several worker threads that process them concurrently, a reaper thread, and a metrics thread (see `pt.isec.directory.core.DirectoryManager` and `pt.isec.directory.threads`).

### Server Cluster

Entry point: `pt.isec.server.MainServer` (package `pt.isec.server`).

Each server instance:

- Registers itself with the directory and sends periodic heartbeats over UDP so the directory knows it is alive.
- Joins a UDP multicast group (`230.30.30.30:3030` by default) shared by every server in the cluster, used for peer-to-peer heartbeats and lightweight, incremental database replication: whenever the primary changes its SQLite database, the resulting SQL statements are broadcast to the other nodes so they can apply the same changes locally, instead of copying the whole database file on every update.
- Keeps its own local SQLite database (one file per server, under a configurable data directory), created from the schema in [`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql) if it does not already exist.
- Accepts TCP client connections (only while acting as primary) through `pt.isec.server.threads.ClientListenerThread`, handling one `pt.isec.server.threads.ClientHandlerThread` per connected client.
- Exposes three application services on top of the database: authentication (`AuthService`), question management (`QuestionService`) and answers/statistics (`AnswerService`).

The network interface used for multicast can be selected automatically (`AUTO`) or pinned to a specific local IP address, which is convenient when a machine has several network adapters (see [Running the System](#running-the-system) below).

### Client Application

Entry point: `pt.isec.client.ClientApplication` (package `pt.isec.client`), a JavaFX desktop application.

On start-up, the client:

1. Sends a UDP discovery request to the directory and waits for the address of the current primary server.
2. Opens a TCP connection to that server and performs a small handshake (server replies with `ACK`/`NACK`).
3. Starts three background threads: one that listens for incoming server messages, one that sends queued outgoing requests, and one that dispatches received responses to the UI via Java `PropertyChangeListener` events (`pt.isec.client.core.ClientManager`).
4. Shows the authentication screen, from which the user can register, log in as a teacher or as a student, and reach the corresponding dashboard.

If the TCP connection is lost, the client automatically tries to rediscover the (possibly new) primary server and reconnect, attempting to resume the previous session transparently; the UI shows a non-blocking "reconnecting" indicator while this happens, and only asks the user to log in again if reconnection ultimately fails.

### Communication Protocol

Two different transport protocols are used, for different purposes:

- **UDP** — used only for directory-related traffic: server registration/heartbeats, client discovery, and server-to-server cluster heartbeats/replication (multicast). Messages are simple text key/value pairs (for example `TYPE=LOGIN`).
- **TCP** — used exclusively between a client and the primary server, once discovery has completed. Messages are Java objects, serialized with the standard `ObjectOutputStream`/`ObjectInputStream` classes and wrapped in a generic `TcpMessage<T>` envelope (`pt.isec.common.messages`) that carries a `MessageType` (an enum with values such as `LOGIN`, `REGISTER_STUDENT`, `CREATE_QUESTION`, `SUBMIT_ANSWER`, `LIST_ANSWERED_QUESTIONS`, and so on) together with a strongly-typed DTO payload (`pt.isec.common.dto.*`).

## Project Structure

```
src/main/java/pt/isec/
├── common/            Shared code between client, server and directory
│   ├── dto/           Data Transfer Objects grouped by feature (auth, question, answer)
│   ├── messages/      TcpMessage/UdpMessage envelopes and MessageType enum
│   ├── model/         Domain model classes (User, Student, Teacher, Question, Option, Answer, ...)
│   └── util/          Small cross-cutting utilities (e.g. the Log helper)
│
├── directory/         Directory service (server registry + primary election)
│   ├── core/          DirectoryManager and its thread-context interface
│   └── threads/       UDP listener, worker pool, reaper and metrics threads
│
├── server/            Quiz server (one node of the replicated cluster)
│   ├── core/          ServerManager (cluster/role coordination) and context interfaces
│   ├── db/            SQLite schema bootstrap and low-level DB command helpers
│   ├── services/      Business logic: authentication, questions, answers
│   └── threads/       Directory/cluster heartbeats, TCP client listener and handler
│
└── client/            JavaFX desktop client
    ├── core/          ClientManager (discovery, connection, reconnection) and context interfaces
    ├── services/      Thin client-side services mirroring the server services
    ├── threads/       Request sender, response handler and server listener threads
    └── ui/            JavaFX views and controllers (authentication, teacher and student dashboards)

src/main/resources/
├── db/schema.sql      SQLite schema applied on first run of a server
├── imgs/              Application icon and logo
└── styles/            CSS stylesheets for the JavaFX UI

docs/                  Assignment brief, evaluation checklist and architecture diagrams
diagrams/              Package diagrams for selected client modules
```

## Data Persistence

Each server keeps its own **SQLite** database file, created automatically from [`schema.sql`](src/main/resources/db/schema.sql) the first time it runs. The schema defines:

- `teacher` / `student` — registered users, with PBKDF2-hashed passwords.
- `config` — a single configuration row holding the current schema/data version used for replication bookkeeping, and the hash of the shared teacher registration code.
- `session` — login/logout/register events used to support reconnection and session resumption.
- `question` / `option` — questions created by teachers, each with a unique access code, a correct option and an availability window.
- `answer` — one row per student/question pair, recording the chosen option.

Passwords and the teacher registration code are never stored in plain text: they are hashed using **PBKDF2WithHmacSHA256** (210 000 iterations) with a random salt per value.

## Technology Stack

- **Java 23** (language level configured in `pom.xml`)
- **Maven** for dependency management and builds
- **JavaFX 20** for the desktop client UI
- **SQLite** (via the `sqlite-jdbc` driver) as the embedded database engine, one file per server
- **Jansi** for ANSI-colored console logging
- **JUnit 5** is declared as a test dependency (no automated tests are part of this assignment)

## Getting Started

### Prerequisites

- **JDK 23** or later. The `pom.xml` targets Java 23 specifically; if you only have an older JDK installed, install a JDK 23 distribution (for example [Eclipse Temurin 23](https://adoptium.net/)) before continuing.
- **Apache Maven** (3.9 or later). If it is not available through your system's package manager, it can be downloaded directly from the [Apache Maven website](https://maven.apache.org/download.cgi) and extracted anywhere on disk; just make sure its `bin` folder is on your `PATH` (or reference it directly, as shown below).
- A machine with a working network stack that allows UDP broadcast/multicast on the local network segment (the default Windows/most Linux firewalls allow this on private networks; corporate or public networks may block it).

### Cloning the Repository

```bash
git clone <this-repository-url>
cd PD_TP_2526
```

### Building the Project

From the project root (where `pom.xml` is located):

```bash
mvn clean compile
```

This downloads all dependencies (including the platform-specific JavaFX binaries) and compiles every module (`common`, `directory`, `server`, `client`) into `target/classes`.

### Running the System

The system requires at least three separate processes, started in this order: **directory**, then **one or more servers**, then **one or more clients**. Each command below should be run from the project root, in its own terminal.

**1. Start the directory**

```bash
java -cp target/classes pt.isec.directory.MainDirectory 9999 1024 65535 17000
```

Arguments (all optional, shown here with their default values): `<udpPort=9999> <queueCapacity=1024> <maxPacketSize=65535> <ttlMillis=17000>`.

**2. Start a server**

The server needs its dependencies on the classpath as well. The simplest way is to materialize them once with Maven and reuse the folder afterwards:

```bash
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
java -cp "target/classes;target/dependency/*" pt.isec.server.MainServer localhost 9999 PROJECT AUTO 5010 17010
```

(on Linux/macOS, replace the `;` classpath separator with `:`)

Arguments: `<directoryHost> <directoryPort> <dataDir|PROJECT|HOME> <multicastInterfaceIp|AUTO> <clientPort> <dbCopyPort>`.

- `directoryHost`/`directoryPort` — where to reach the directory started in step 1.
- `dataDir` — where the SQLite file is stored; use the literal `PROJECT` to place it in a `data/` folder next to the project, `HOME` to place it under the user's home directory, or an explicit path.
- `multicastInterfaceIp` — the local network interface used for cluster multicast traffic; `AUTO` picks the first suitable interface automatically, which is the right choice on most machines. Only pin a specific IP if a machine has several network adapters and automatic selection picks the wrong one.
- `clientPort` — TCP port this server listens on for client connections.
- `dbCopyPort` — TCP port reserved for full-database-copy requests between servers.

**3. Start a client**

The client is a JavaFX application, so it is started through the `javafx-maven-plugin` configured in `pom.xml`:

```bash
mvn javafx:run
```

The client currently expects the directory to be reachable at `localhost:9999` (see the constants at the top of `pt.isec.client.ClientApplication`); if the directory runs on another machine, adjust those constants and rebuild.

You can start multiple clients (in separate terminals) to simulate several teachers/students using the system concurrently.

### Running a Server Cluster

To observe primary election, replication and failover, start more than one server pointed at the same directory, each with distinct TCP ports:

```bash
java -cp "target/classes;target/dependency/*" pt.isec.server.MainServer localhost 9999 PROJECT AUTO 5010 17010
java -cp "target/classes;target/dependency/*" pt.isec.server.MainServer localhost 9999 PROJECT AUTO 5011 17011
java -cp "target/classes;target/dependency/*" pt.isec.server.MainServer localhost 9999 PROJECT AUTO 5012 17012
```

The first one to register with the directory becomes primary and starts serving clients; the others stay as backups, replicating the primary's database in the background. Stopping the primary process (Ctrl+C) makes the directory promote the next registered server after its heartbeat expires (up to `ttlMillis`, 17 seconds by default), and connected clients reconnect to it automatically.

## Troubleshooting

**`mvn` is not recognized / Maven not found**
Maven is not on your `PATH`. Either add its `bin` directory to `PATH`, or call the executable with its full path (for example `C:\tools\apache-maven-3.9.16\bin\mvn`).

**Build fails with a Java version error**
Confirm `java -version` and `mvn -v` both report Java 23. If multiple JDKs are installed, set `JAVA_HOME` to the JDK 23 installation before running Maven.

**`Address already in use` when starting the directory or a server**
Another process is already using that port. Check with `netstat -ano | findstr :<port>` (Windows) or `lsof -i :<port>` (Linux/macOS) and stop the conflicting process, or choose a different port.

**Client cannot discover a server ("Unable to contact directory/server")**
Make sure the directory is started first and is still running, and that a server has registered with it (the directory logs `servers=1` once one is registered). Also confirm no firewall is blocking UDP traffic on the ports being used.

**Servers do not see each other / replication does not seem to happen**
This is almost always a multicast interface problem. Prefer `AUTO` for the `multicastInterfaceIp` argument; if you must pin an interface manually, make sure the chosen IP belongs to an interface that is up, non-loopback and multicast-capable. On machines with a VPN adapter or several virtual network adapters, `AUTO` may need to skip a few before finding a working one — check the server log line `[MC] interface multicast selecionada: ...` to see which one was picked.

**JavaFX-related errors when running the client without `mvn javafx:run`**
The client depends on platform-specific JavaFX modules resolved by Maven. Always launch it through `mvn javafx:run` (or configure an equivalent module-path manually); running the compiled classes with a plain `java -cp ...` command will fail with missing JavaFX classes.

## Diagrams

- [`docs/Geral.png`](docs/Geral.png) — overall system overview.
- [`docs/Comunicação_geral.png`](docs/Comunicação_geral.png) — general communication flow between directory, servers and clients.
- [`docs/Directory.png`](docs/Directory.png), [`docs/Server.png`](docs/Server.png), [`docs/Client.png`](docs/Client.png) — per-component diagrams.
- [`diagrams/`](diagrams/) — package-level diagrams for selected client modules (`core`, `services`, `threads`).

## Authors

Developed by Rui Casaca, Davi Gama and Afonso David, for the Distributed Programming course (2025/2026), Instituto Superior de Engenharia de Coimbra (ISEC).

## License

This project was developed for academic purposes as part of a university course assignment. No specific open-source license is granted; please contact the authors before reusing this code outside an academic context.
