+Server {
    *suggestedServerPort {
        ^InitializeProvider.suggestedServerPort ?? { 57110 }
    }
    
    *defaultNetAddr {
        ^NetAddr.new("127.0.0.1", Server.suggestedServerPort)
    }
    
    *fromName { |name|
        ^Server.named[name] ?? {
            Server(name, Server.defaultNetAddr)
        }
    }
    
    addr_ { |netAddr|
        addr = netAddr ?? { Server.defaultNetAddr };
        inProcess = addr.addr == 0;
        isLocal = inProcess || { addr.isLocal };
        remoteControlled = isLocal.not;
    }
}