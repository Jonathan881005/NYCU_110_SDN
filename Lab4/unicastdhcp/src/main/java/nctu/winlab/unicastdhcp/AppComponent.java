package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Path;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** Skeletal ONOS application component. */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NameConfigListener cfgListener = new NameConfigListener();
    private final ConfigFactory<ApplicationId, NameConfig> factory =
      new ConfigFactory<ApplicationId, NameConfig>(
          APP_SUBJECT_FACTORY, NameConfig.class, "UnicastDhcpConfig") {
        @Override
        public NameConfig createConfig() {
          return new NameConfig();
        }
      };


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;
  
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    private DhcpPacketProcessor processor = new DhcpPacketProcessor();

    private Map<DeviceId, Map<MacAddress, PortNumber>> mac_to_port = new HashMap<DeviceId, Map<MacAddress, PortNumber>>();
    
    private Map<MacAddress, DeviceId> mac_to_deviceid = new HashMap<MacAddress, DeviceId>();

    private ApplicationId appId;

    private String dhcpserverId;
    private String[] finalplace;
    private DeviceId install_Device; 
    private PortNumber install_port1, install_port2;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        packetService.addProcessor(processor, PacketProcessor.director(2));

        requestIntercepts();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);

        packetService.removeProcessor(processor);
        processor = null;
 
        withdrawIntercepts();
        log.info("Stopped");
    }

    private void requestIntercepts() {
        // TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
        //     .matchEthType(Ethernet.TYPE_IPV4)
        //     .matchIPProtocol(IPv4.PROTOCOL_UDP);
        // packetService.requestPackets(selectorServer.build(), PacketPriority.REACTIVE, appId);


        TrafficSelector.Builder selector =  DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
            .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
        packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);

        TrafficSelector.Builder selector2 =  DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
            .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
        packetService.requestPackets(selector2.build(), PacketPriority.CONTROL, appId);
    }

    private void withdrawIntercepts() {
        // TrafficSelector.Builder selectorServer = DefaultTrafficSelector.builder()
        //     .matchEthType(Ethernet.TYPE_IPV4)
        //     .matchIPProtocol(IPv4.PROTOCOL_UDP);
            
        // packetService.cancelPackets(selectorServer.build(), PacketPriority.REACTIVE, appId);

		TrafficSelector.Builder selector =  DefaultTrafficSelector.builder()
        .matchEthType(Ethernet.TYPE_IPV4)
        .matchIPProtocol(IPv4.PROTOCOL_UDP)
        .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
        .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
        packetService.cancelPackets(selector.build(), PacketPriority.CONTROL, appId);
        
        TrafficSelector.Builder selector2 =  DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .matchIPProtocol(IPv4.PROTOCOL_UDP)
            .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
            .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
        packetService.cancelPackets(selector2.build(), PacketPriority.CONTROL, appId);
    }


    private class DhcpPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {


            // log.info("-------------start to process--------------");

            if (context.isHandled()) {
                // log.info("is handled!");
                // log.info("-------------end of process--------------");
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null)
                return;
            


            MacAddress srcMAC = ethPkt.getSourceMAC();
            PortNumber in_port= context.inPacket().receivedFrom().port();

            // MacAddress dstMAC = ethPkt.getDestinationMAC();
            DeviceId   srcDid = context.inPacket().receivedFrom().deviceId(); 
            
            // Update mac_to_deviceid
            if(!mac_to_deviceid.containsValue(srcDid))
                mac_to_deviceid.put(srcMAC, srcDid);

            //
            Set<Path> p;
            if(ethPkt.isBroadcast()) { // from client to server

                DeviceId dstDid = DeviceId.deviceId(finalplace[0]);
                log.info("srcDid = {}, dstDid = {}", srcDid, dstDid);
                if(!srcDid.equals(dstDid)){
                    p = pathService.getPaths(srcDid, dstDid); // src -> client, dst -> server
                    if(p.size()==1) {
                        Iterator<Path> iter = p.iterator();
                        Path pp = iter.next();
                        // log.info("get a path, pp.size() = {}", pp.links().size());
                        install_Device = srcDid;
                        install_port1 = in_port;
                        install_port2 = pp.links().get(0).src().port();
                        installRuleCtoS(context, install_Device, install_port1, install_port2);
                        installRuleStoC(context, install_Device, install_port2, install_port1);

                        for (int i = 0; i < pp.links().size()- 1 ; i++){
                            install_Device = pp.links().get(i).dst().deviceId();
                            install_port1 = pp.links().get(i).dst().port();
                            install_port2 = pp.links().get(i+1).src().port();
                            installRuleCtoS(context, install_Device, install_port1, install_port2);
                            installRuleStoC(context, install_Device, install_port2, install_port1);
                        }

                        install_Device = dstDid;
                        install_port1 = pp.links().get(pp.links().size() - 1).dst().port();
                        install_port2 = PortNumber.fromString(finalplace[1]);
                        installRuleCtoS(context, install_Device, install_port1, install_port2);
                        installRuleStoC(context, install_Device, install_port2, install_port1);
                    }
                    else {
                        log.info("warning, p.size() != 1!!!! fuck");
                    }
                    log.info("Not on a same sw, have a valid path, install rule...");
                }
                else{
                    install_Device = srcDid;
                    install_port1 = in_port;
                    install_port2 = PortNumber.fromString(finalplace[1]);
                    if(install_port1.equals(install_port2))
                        return;
                        // 這行特殊狀況 debug 先用
                    installRuleCtoS(context, install_Device, install_port1, install_port2);
                    installRuleStoC(context, install_Device, install_port2, install_port1);
                    log.info("It's on a same sw, install rule on a single switch~~~");
                }
            }
            else {
                // DeviceId dstDid = mac_to_deviceid.get(dstMAC);  // from server to client
                // p = pathService.getPaths(srcDid, dstDid); // src -> server, dst -> client
                // log.info("from server to client, p.size() = {}", p.size());
                // log.info("srcDid：{}, dstDid：{}", srcDid.toString(), dstDid.toString());
                log.info("You shouldn't be here bro = =");
            }

            // 最後要 packetOut，不然一開始的封包都會被丟掉
            packetOut(context, PortNumber.TABLE);
        }
    }




    // Sends a packet out the specified port.
    // 送出封包到指定的port
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
        log.info("packetOut! {}", portNumber.toString());
    }


    private void installRuleCtoS(PacketContext context, DeviceId installDevice, PortNumber inport, PortNumber outport) {
        
        Ethernet inPkt = context.inPacket().parsed();

        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchInPort(inport)
                .matchEthSrc(inPkt.getSourceMAC()); // EthSrc match client mac

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outport)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(50000)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(20)
                .add();

        flowObjectiveService.forward(install_Device,
                                     forwardingObjective);

        log.info("install rules from CCClient to server at {}, in_port = {}, out_port ={}!!", installDevice.toString(), inport.toString(), outport.toString());
    }


    private void installRuleStoC(PacketContext context, DeviceId installDevice, PortNumber inport, PortNumber outport) {
        
        Ethernet inPkt = context.inPacket().parsed();

        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchInPort(inport)
                .matchEthDst(inPkt.getSourceMAC()); // EthDst match client mac

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outport)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(50000)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(30)
                .add();

        flowObjectiveService.forward(install_Device,
                                     forwardingObjective);

        log.info("install rules from SSServer to client  at {}, in_port = {}, out_port ={}!!", installDevice.toString(), inport.toString(), outport.toString());
    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                if (config != null) {
                log.info("DHCP server is at {}", config.name());
                dhcpserverId = config.name(); // Sure? String to Deviceid?
                finalplace = dhcpserverId.split("/");
                // finalplace：[0] => deviceId， [1] => port
                }
            }
        }
    }
}