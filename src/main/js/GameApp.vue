  <template>
    <div>
      <h1>Main</h1>
      <div>
        <label>{{ text }}</label>
      </div>
        <div>
          <input type="button" value="Connect" id="connect" v-on:click="handleConnectButtonPressed">
        </div>
        <div>
            <input type="button" value="Join game" id="joinGame" v-on:click="handleJoinButtonPressed"/>
        </div>
        <div>
            <input type="button" value="Send phrase" id="sendEcho" v-on:click="handleSendEchoRequested"/>
        </div>
    </div>
</template>

<script>
//    import Main from '@/components/Main'
    import gameService from './gameService'
    import utils from './utils'

    export default {
        name: 'GameApp',
        data: function() {
          return {
            gameService: null,
            conn: null,
            text: "not changed yer"
          }
        },
        methods: {
            handleJoinButtonPressed: function (e) {
                console.log(`Join requested ${e}`);
                gameService.sendJoinGameRequest(this.$data.conn)
            },

            handleSendEchoRequested: function (e) {
                console.log('Sending test echo');
                gameService.sendToWebSocket(this.$data.conn,"Test echo")
            },
            handleConnectButtonPressed: function (e) {
              console.log('Connecting to websocket')
              this.$data.conn = gameService.openWebSocketConnection(utils.webSocketUrl)
              console.log(`Conn: ${this.$data.conn}`)
            }
        }
    }
</script>

<style>
    body, input, button {
        font-family: Helvetica, Arial, sans-serif;
        font-size: 13pt;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
    }

    button, input {
        padding: 4pt;
    }
</style>