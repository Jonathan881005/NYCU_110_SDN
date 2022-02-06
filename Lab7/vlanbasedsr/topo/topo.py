#!/usr/bin/python

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.log import setLogLevel
from mininet.node import RemoteController
from mininet.cli import CLI
from mininet.node import Node
from mininet.link import TCLink

class MyTopo( Topo ):

    def __init__( self ):
        Topo.__init__( self )

        h1 = self.addHost("h1", ip = '10.0.2.1/24', mac='ea:e9:78:fb:fd:01', defaultRoute='h1-eth0')
        h2 = self.addHost("h2", ip = '0.0.0.0', mac='ea:e9:78:fb:fd:02', defaultRoute='h2-eth0')
        h3 = self.addHost("h3", ip = '0.0.0.0', mac='ea:e9:78:fb:fd:03', defaultRoute='h3-eth0')

        h4 = self.addHost("h4", ip = '10.0.3.1/24', mac='ea:e9:78:fb:fd:04', defaultRoute='h4-eth0')
        h5 = self.addHost("h5", ip = '10.0.3.2/24', mac='ea:e9:78:fb:fd:05', defaultRoute='h5-eth0')
        
        s1 = self.addSwitch("s1") # 101
        s2 = self.addSwitch("s2") # 102
        s3 = self.addSwitch("s3") # 103

        self.addLink(s1, s2)
        self.addLink(s1, s3)

        self.addLink(h1, s2)
        self.addLink(h2, s2)
        self.addLink(h3, s2)

        self.addLink(h4, s3)
        self.addLink(h5, s3)

def run():
    topo = MyTopo()
    net = Mininet(topo=topo, controller=None, link=TCLink)
    net.addController('c0', controller=RemoteController, ip='127.0.0.1', port=6653)

    net.start()

    print("[+] Run DHCP server")
    dhcp = net.getNodeByName('h1')
    # dhcp.cmdPrint('service isc-dhcp-server restart &')
    dhcp.cmdPrint('/usr/sbin/dhcpd 4 -pf /run/dhcp-server-dhcpd.pid -cf ./dhcpd.conf %s' % dhcp.defaultIntf())

    CLI(net)
    print("[-] Killing DHCP server")
    dhcp.cmdPrint("kill -9 `ps aux | grep h1-eth0 | grep dhcpd | awk '{print $2}'`")
    net.stop()

if __name__ == '__main__':
    setLogLevel('info')
    run()