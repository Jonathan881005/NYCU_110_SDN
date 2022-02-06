from mininet.topo import Topo

class Project2_Topo_310551167( Topo ):
    def __init__( self ):
        Topo.__init__( self )

        # Add hosts
        h1 = self.addHost( 'h1' , mac = "00:00:00:00:00:01", ip = '192.168.130.1/27')
        h2 = self.addHost( 'h2' , mac = "00:00:00:00:00:02", ip = '192.168.130.2/27')
        h3 = self.addHost( 'h3' , mac = "00:00:00:00:00:03", ip = '192.168.130.2/27')

        # Add switches
        s1 = self.addSwitch( 's1' )

        # Add links
        self.addLink( h1, s1 )
        self.addLink( h2, s1 )
        self.addLink( h3, s1 )

topos = { 'topo_demo_310551167': Project2_Topo_310551167 }