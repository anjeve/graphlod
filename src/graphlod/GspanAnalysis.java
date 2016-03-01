package graphlod;

import de.parsemis.Miner;
import de.parsemis.graph.Graph;
import de.parsemis.miner.environment.Settings;
import de.parsemis.miner.general.Fragment;
import graphlod.dataset.Dataset;

import java.util.Collection;

public class GspanAnalysis {

    void run(Dataset dataset) {
        Settings<String, String> settings = Settings.parse(new String[]{});
        Collection<Graph<String,String>> graphs = null;
        Collection<Fragment<String, String>> result = Miner.mine(graphs, settings);
    }

}
