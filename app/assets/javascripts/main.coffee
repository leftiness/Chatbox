#TODO Rooms in the UI
#TODO knockout for keyup event to send message

_debug = true

debug = (message) ->
    if _debug
        console.log message

_vm =
    room: ko.observable()
    name: ko.observable()
    messages: ko.observableArray()
    message: ko.observable()
    keydown: (data, event) ->
        if event.keyCode == 13
            this.submit()
            false
        else
            true
    submit: ->
        text = (this.message() || '').trim()
        if text.length > 0
            arr = text.split(' ')
            request = switch arr[0]
                when '/new'
                    roomName: arr[1]
                    userName: arr[2]
                    messageType: 'newRoom'
                when '/join'
                    roomId: arr[1]
                    userName: arr[2]
                    messageType: 'joinRoom'
                when '/leave'
                    roomId: arr[1]
                    messageType: 'leaveRoom'
                when '/disconnect'
                    messageType: 'disconnectUser'
                when '/name'
                    userName: arr[1]
                    roomId: this.room()
                    messageType: 'nameUser'
                when '/promote'
                    userName: arr[1]
                    roomId: this.room()
                    messageType: 'promoteUser'
                else
                    roomId: this.room()
                    messageText: text
                    messageType: 'messageIn'
            debug "Sending request #{JSON.stringify request}"
            _ws.send JSON.stringify request
            this.message ''
    receive: (response) ->
        debug "Received data #{JSON.stringify response}"
        switch response.messageType
            when 'messageOut'
                #TODO Messages should go to specific rooms...
                this.messages.push response
            when 'systemMessage'
                this.messages.push response
            when 'globalSystemMessage'
                #TODO Message should go to all rooms
                this.messages.push response
            when 'nameUser'
                #TODO Name should be in specific room...
                this.name response.userName
                this.room response.roomId
    
init_ws = ->
    def = Q.defer()
    # TODO When the client is not the same machine as the server, I'll need the IP of that server here...
    host = 'ws://localhost:9000/chat'
    debug "Starting up websocket at #{host}"
    ws = new WebSocket(host) 
    ws.onmessage = (event) -> 
        _vm.receive JSON.parse event.data
    ws.onerror = (err) -> 
        console.error err
        #TODO Reconnect strategy instead of just console.error()
    ws.onopen = -> 
        debug 'Websocket is open'
        def.resolve ws
    def.promise

debug 'Starting up client'

_ws = undefined
init_ws().done (ws) -> _ws = ws
ko.applyBindings _vm;