package nctu.winlab.bridge;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.net.flowobjective.ForwardingObjective;
// import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@Component(immediate = true)
public class AppComponent {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    // our application-specific event handler
    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    // mac address table
    private Map<DeviceId, Map<MacAddress, PortNumber>> mac_to_port = new HashMap<DeviceId, Map<MacAddress, PortNumber>>();

    //代表該應用程式的id
    private ApplicationId appId;

    private ApplicationId core_appId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.bridge-app");
        core_appId = coreService.registerApplication("core");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
        log.info("--------------bridge-app Started!!=w=----------------");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;
        withdrawIntercepts();

        log.info("--------------bridge-app Stopped~~QAQ----------------");
    }

    private void requestIntercepts() {  
        packetService.requestPackets(buildArpSelector(), PacketPriority.REACTIVE, appId);
        packetService.requestPackets(buildIPv4Selector(), PacketPriority.REACTIVE, appId);
    }

    private void withdrawIntercepts() {
        packetService.cancelPackets(buildArpSelector(), PacketPriority.REACTIVE, appId);
        packetService.cancelPackets(buildIPv4Selector(), PacketPriority.REACTIVE, appId);
    }

    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            if (context.isHandled()) {
                return;
            }

            MacAddress srcMAC = ethPkt.getSourceMAC();
            MacAddress dstMAC = ethPkt.getDestinationMAC();
            PortNumber in_port= context.inPacket().receivedFrom().port();
            DeviceId   sw     = context.inPacket().receivedFrom().deviceId(); 
          
            PortNumber out_port;

            if(mac_to_port.containsKey(sw)){
                Map tempMap = mac_to_port.get(sw);
                if(!tempMap.containsKey(srcMAC)){
                    log.info("Add MAC address ==> switch: {}, MAC: {}, port: {}", sw, srcMAC, in_port);
                    tempMap.put(srcMAC, in_port);
                }
            }else{
                mac_to_port.put(sw,new HashMap<MacAddress, PortNumber>());
                Map tempMap = mac_to_port.get(sw);
                tempMap.put(srcMAC, in_port);
                log.info("Add MAC address ==> switch: {}, MAC: {}, port: {}", sw, srcMAC, in_port);
            }

            Map<MacAddress, PortNumber> tempMap = mac_to_port.get(sw);
            if(tempMap.containsKey(dstMAC)){
                out_port=tempMap.get(dstMAC);
                log.info("MAC {} is matched on {}! Install flow rule!", dstMAC, sw);
                installRule(context, out_port);
            }
            else{
                log.info("MAC {} is missed on {}! Flood packet!", dstMAC ,sw);
                flood(context);
            }
            
        }
    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    private void flood(PacketContext context) {
        packetOut(context, PortNumber.FLOOD);
    }

    private void installRule(PacketContext context, PortNumber portNumber) {

        Ethernet inPkt = context.inPacket().parsed();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(DefaultTrafficSelector.builder()
                .matchEthSrc(inPkt.getSourceMAC())
                .matchEthDst(inPkt.getDestinationMAC()).build())

                .withTreatment(DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build())

                .withPriority(30)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(30)
                .add();

        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                                     forwardingObjective);


        // FlowRule fr = DefaultFlowRule.builder()
        // .withSelector(DefaultTrafficSelector.builder()
        // .matchEthSrc(inPkt.getSourceMAC())
        // .matchEthDst(inPkt.getDestinationMAC()).build())

        // .withTreatment(DefaultTrafficTreatment.builder()
        // .setOutput(portNumber).build())

        // .forDevice(context.inPacket().receivedFrom().deviceId())
        // .withPriority(30)
        // .makeTemporary(30)
        // .fromApp(appId).build();

        // flowRuleService.applyFlowRules(fr);


        packetOut(context, portNumber);

        // log.info("install rules!");
    }

    private TrafficSelector buildArpSelector() {
        return DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP)
                .build();
    }

    
    private TrafficSelector buildIPv4Selector() {
        return DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .build();
    }
}