#TODO Room
#TODO Get name from websocket
#TODO knockout for keyup event to send message

_vm =
    room: ko.observable('The Room')
    name: ko.observable('Anonymous12345')
    messages: ko.observableArray()
    message: ko.observable()
    keydown: (data, event) ->
        if event.keyCode == 13
            this.submit()
            false
        else
            true
    submit: ->
        message = (this.message() || '').trim()
        if message.length > 0
            request = switch message.split(' ')[0]
                else
                    name: this.name()
                    room: this.room()
                    message: message
                    type: 'message'
            _ws.send JSON.stringify request
            this.message ''
    receive: (response) ->
        switch response.type
            when 'message'
                this.messages.push response
    
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