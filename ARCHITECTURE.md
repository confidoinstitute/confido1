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

## Persistence

- Entities and managers
	- Global state, ref and deref
- MongoDB

## Frontend structure

- URL route mapping
- HTTP Client and sendData
- Layouts
- Contexts used
- Component grouping
- Hooks
