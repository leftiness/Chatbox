#TODO Room
#TODO Get name from websocket
#TODO knockout for keyup event to send message

_vm =
    room: ko.observable('The Room')
    name: ko.observable('Anonymous12345')
    messages: ko.observableArray()
    message: ko.observable()
    submit: ->
        if this.message().trim().length > 0
            request =
                name: this.name()
                message: this.message().trim()
                room: this.room()
            _ws.send JSON.stringify request
            this.message ''
    receive: (message) -> 
        this.messages.push message
        # TODO Instead of blindly pushing message, look at what is in the message and act upon it.
        # For example, if I'm sending you your automatically assigned name, then that's not something
        # that has to go immediately on the chat log. I need to get and save that name, too.
    
init_ws = ->
    def = Q.defer()
    # TODO When the client is not the same machine as the server, I'll need the IP of that server here...
    host = 'ws://localhost:9000/chat'
    ws = new WebSocket(host) 
    ws.onmessage = (event) -> 
        _vm.receive JSON.parse event.data
    ws.onerror = (err) -> 
        console.error err
        #TODO Reconnect strategy instead of just console.error()
    ws.onopen = -> 
        def.resolve ws
    def.promise

_ws = undefined
init_ws().done (ws) -> _ws = ws
ko.applyBindings _vm;