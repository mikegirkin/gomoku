<template>
    <div>
        <h1>Main</h1>
        <div>
            <input type="button" value="Join game" id="joinGame" v-on:click="handleJoinRequested"/>
        </div>
    </div>
</template>

<script>
//    import Main from '@/components/Main'
    import gameService from '@/gameService'

    export default {
        name: 'GameApp',
        methods: {
            handleJoinRequested: function (e) {
                console.log(`Join requested ${e}`);
                gameService.sendJoinGameRequest()
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