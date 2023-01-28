# Confido architecture

Confido is designed as a client-server web application. The server (backend)
is responsible for managing the data while the client is an SPA frontend and
provides the user interface.

## Data Flow

There are two directions to which the data communicates between the clients and
the server. These directions are asymmetric.

In most cases, the communication is done via JSON messages. In some cases the
amount of data transferred may be very high and CBOR is used instead.

### Receiving data from the server

There are in general two types of data that the client may request: one-shot
and continuous data. The one-shot data (such as CSV export or login result) are
sent as body of HTTP requests and can be done at any time.

On the other hand, continuous data are subscription-based: the client starts a
Websocket connection at the appropriate address. This socket is then used as an
unidirectional channel. The server holds all of the currently open sockets and,
when there is a change to this data, refreshes all of them. The clients then
receive a new complete snapshot of the data.

In order for a client to subscribe to server data, they need to have an active
session. While this session is persistent, the active channels are part of the
in-memory transient data. Should the server restart, all of the channels are
broken and clients must subscribe anew.

### Client to Server

All of the client actions are handled using HTTP requests. In most cases, the
server responds by a refresh of the relevant subscription channels and sends
empty response to the client. If a more detailed feedback is needed, it is sent
as the response body. In case the request fails, the error message is sent as
an error HTTP response body.

## Global state and configuration

The server and client both bookkeep the global state of the instance in their
memory. The server maintains the full state, including potentially vulnerable
data. In addition, the client may have insufficient authorization to see parts
of this state.

For this purpose, the Ministry of Truth provided us with a State
censor. As the subscription channel is refreshed, before the data is sent to
the client, it is censored, receiving only the relevant subset of this state.

The (censored) global state has its dedicated subscription channel, which also
handles other functions:

- Version management: If the server's version differs from the client, the
  client is notified and completely reloads itself.
- Session creation: A new client starts their session by subscribing to the
  global state.

### Instance configuration

Each Confido instance may have its static configuration. This configuration can
be changed only by fully restarting the server.

## Data model

The Confido data is made up of *entities* and auxilliary data. The entities can
be referred to by other objects or used as indices. Auxilliary data exist
either as singleton objects, or are indexed by an entity. Data is considered
immutable.

Each entity contains a unique string `id` which is used as its identifier. Any
reference to this entity is then done via this `id`. If an entity needs to be
referenced, there must exist a source of data containing the index
of these entites. In general, this index exists in the (censored) global
state. Then, if we wish to obtain the entity from its reference, this index is
looked up.

### Persistence

The persistence of the global state is handled by MongoDB. Each entity type has
its own collection. All references are stored as the corresponding `id`
strings.

Most of the data is kept synchronized between the persistent database and the
in-memory state. Thus, only modification requests access the database. There
are exceptions when the sheer volume of the data would be too much to be stored
in-memory. In such case, this data is loaded on-demand from the database.

## Frontend structure

- URL route mapping
- HTTP Client and sendData
- Layouts
- Contexts used
- Component grouping
- Hooks
