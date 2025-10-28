package app.controller;

import model.Contact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import app.service.NodeDiscoveryService;
import java.util.List;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    private final NodeDiscoveryService nodeDiscoveryService;

    @Autowired
    public NodeController(NodeDiscoveryService nodeDiscoveryService) {
        this.nodeDiscoveryService = nodeDiscoveryService;
    }


    @GetMapping("/node/all")
    public List<Contact> getAllNodes() {
        return nodeDiscoveryService.getKnownNodes();
    }
}
