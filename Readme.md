# NYCU_110_SDN
Practiced SDN with ONOS

### Lab 1 + Lab 2 
- Exploring ONOS, mininet, wireshark
- Learning the usage of flow rule, ONOS GUI, Rest API

### Lab 3 - Learning bridge
* Write an application of learning bridge function
* Update MAC to port table when receiving packet-in

### Lab 4 - Unicast DHCP Applcation
* Dynamically set DHCP server’s connect point through REST API (configuration service)
* Compute path between DHCP client and DHCP server
* Install flow rules to forward DHCP transaction traffic

### Lab 5 - Proxy ARP
* If no mapping is found -> flood ARP request to all edge ports
* If mapping of requested IP to MAC address has already been learned -> Send Packet-Out of ARP Reply directly

### Lab 6 - Software Router and Containerization
* Exploring Quagga, Docker
* Creating a topology simulating the BGP protocol in a VM with Quagga

### Lab 7 - VLAN-based Segment Routing
* All flow/group rules should be installed once controller receives configuration
* Forward packets with label switching and source routing mechanism
* If there are multiple paths with same hop count, use SELECT group to achieve load balancing