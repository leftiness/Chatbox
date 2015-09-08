#TODO Room
#TODO Get name from websocket
#TODO knockout for keyup event to send message

_debug = true

debug = (message) ->
    if _debug
        console.log message

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
                when '/join'
                    room: message.slice(6)
                    type: 'join'
                when '/name'
                    name: this.name()
                    room: this.room()
                    change: message.slice(6)
                    type: 'name'
                else
                    name: this.name()
                    room: this.room()
                    message: message
                    type: 'message'
            debug "Sending request #{JSON.stringify request}"
            _ws.send JSON.stringify request
            this.message ''
    receive: (response) ->
        debug "Received a message #{JSON.stringify response}"
        switch response.type
            when 'message'
                this.messages.push response
            when 'name'
                this.name response.change
    
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