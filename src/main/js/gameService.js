import axios from 'axios'

const self = {
    sendJoinGameRequest(conn) {
        console.log("Sending Http join game request");
        let request = {
            type: 'RequestJoinGame'
        }
        self.sendToWebSocket(conn, JSON.stringify(request))
    },

    openWebSocketConnection(url) {
        console.log(`opening websocket connection ${url}`);
        let conn = new WebSocket(url);

        conn.onclose = function (event) {
            console.log(`Connection closed ${event.code} ${event.reason}`)
        };

        conn.onmessage = function (event) {
            console.log(`Message received ${JSON.stringify(event.data)}`)
        };

        return conn
    },

    sendToWebSocket(conn, text) {
        if (conn == null) {
            console.log("conn == null")
            return;
        }
        if (conn.readyState !== 1) {
            console.log(`Connection is not open ${conn.readyState}`);
            return;
        }
        console.log("sending data through websocket");
        conn.send(text);
    }
}

export default self