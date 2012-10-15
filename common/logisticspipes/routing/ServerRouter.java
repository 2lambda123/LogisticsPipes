/** 
 * Copyright (c) Krapht, 2011
 * 
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package logisticspipes.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import logisticspipes.config.Configs;
import logisticspipes.interfaces.ILogisticsModule;
import logisticspipes.interfaces.routing.ILogisticsPowerProvider;
import logisticspipes.interfaces.routing.IPowerRouter;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.network.NetworkConstants;
import logisticspipes.network.packets.PacketRouterInformation;
import logisticspipes.pipes.PipeItemsBasicLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.RoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.ItemIdentifier;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Packet250CustomPayload;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import buildcraft.api.core.Orientations;
import buildcraft.api.core.Position;
import buildcraft.transport.TileGenericPipe;

public class ServerRouter implements IRouter, IPowerRouter {

	private class LSA {
		public IRouter source;
		public HashMap<IRouter, Integer> neighboursWithMetric;
	}
	
	public HashMap<RoutedPipe, ExitRoute> _adjacent = new HashMap<RoutedPipe, ExitRoute>();

	private static int _LSDVersion = 0;
	private int _lastLSDVersion = 0;
	
	private static RouteLaser _laser = new RouteLaser();
	
	private static LinkedList<LSA> SharedLSADatabase = new LinkedList<LSA>();
	private LSA _myLsa = new LSA();
		
	/** Map of router -> orientation for all known destinations **/
	public HashMap<IRouter, Orientations> _routeTable = new HashMap<IRouter, Orientations>();
	public HashMap<IRouter, Integer> _routeCosts = new HashMap<IRouter, Integer>();
	public LinkedList<IRouter> _externalRoutersByCost = null;
	
	/** IRouter Tree **/
	//public LogisticsNetworkTree tree = new LogisticsNetworkTree(this, new LinkedHashMap<LogisticsNetworkTree, ExitRoute>(), null);

	private boolean _blockNeedsUpdate;
	private boolean init = false;
	
	public final UUID id;
	private int _dimension;
	private final int _xCoord;
	private final int _yCoord;
	private final int _zCoord;
	
	public static void resetStatics(){
		SharedLSADatabase.clear();
		_LSDVersion = 0;
		_laser = new RouteLaser();
	}
	
	public ServerRouter(UUID id, int dimension, int xCoord, int yCoord, int zCoord){
		this.id = id;
		this._dimension = dimension;
		this._xCoord = xCoord;
		this._yCoord = yCoord;
		this._zCoord = zCoord;
		_myLsa = new LSA();
		_myLsa.source = this;
		_myLsa.neighboursWithMetric = new HashMap<IRouter, Integer>();
		SharedLSADatabase.add(_myLsa);
	}

	@Override
	public CoreRoutedPipe getPipe(){
		World worldObj = MainProxy.getWorld(_dimension);
		if(worldObj == null) {
			return null;
		}
		TileEntity tile = worldObj.getBlockTileEntity(_xCoord, _yCoord, _zCoord);
		
		if (!(tile instanceof TileGenericPipe)) return null;
		TileGenericPipe pipe = (TileGenericPipe) tile;
		if (!(pipe.pipe instanceof CoreRoutedPipe)) return null;
		return (CoreRoutedPipe) pipe.pipe;
	}

	private void ensureRouteTableIsUpToDate(){
		if (_LSDVersion > _lastLSDVersion){
			CreateRouteTable();
			//tree = getNetworkTree(null, 0);
			_externalRoutersByCost = null;
			_lastLSDVersion = _LSDVersion;
		}
	}

	@Override
	public HashMap<IRouter, Orientations> getRouteTable(){
		ensureRouteTableIsUpToDate();
		return _routeTable;
	}
	
	@Override
	public LinkedList<IRouter> getIRoutersByCost() {
		ensureRouteTableIsUpToDate();
		if (_externalRoutersByCost  == null){
			_externalRoutersByCost  = new LinkedList<IRouter>();
			
			LinkedList<RouterCost> tempList = new LinkedList<RouterCost>();
			outer:
			for (IRouter r : _routeCosts.keySet()){
				for (int i = 0; i < tempList.size(); i++){
					if (_routeCosts.get(r) < tempList.get(i).cost){
						tempList.add(i, new RouterCost(r, _routeCosts.get(r)));
						continue outer;
					}
				}
				tempList.addLast(new RouterCost(r, _routeCosts.get(r)));
			}
			
			while(tempList.size() > 0){
				_externalRoutersByCost.addLast(tempList.removeFirst().router);
			}
			_externalRoutersByCost.addFirst(this);
		}		
		return _externalRoutersByCost;
	}
	
	@Override
	public UUID getId() {
		return this.id;
	}
	

	/**
	 * Rechecks the piped connection to all adjacent routers as well as discover new ones.
	 */
	private void recheckAdjacent()	{
		boolean adjacentChanged = false;
		CoreRoutedPipe thisPipe = getPipe();
		if (thisPipe == null) return;
		HashMap<RoutedPipe, ExitRoute> adjacent = PathFinder.getConnectedRoutingPipes(thisPipe.container, Configs.LOGISTICS_DETECTION_COUNT, Configs.LOGISTICS_DETECTION_LENGTH);
		
		for (RoutedPipe pipe : _adjacent.keySet()){
			if(!adjacent.containsKey(pipe))
				adjacentChanged = true;
		}
		
		for (RoutedPipe pipe : adjacent.keySet())	{
			if (!_adjacent.containsKey(pipe)){
				adjacentChanged = true;
				break;
			}
			ExitRoute newExit = adjacent.get(pipe);
			ExitRoute oldExit = _adjacent.get(pipe);
			
			if (newExit.exitOrientation != oldExit.exitOrientation || newExit.metric != oldExit.metric)	{
				adjacentChanged = true;
				break;
			}
		}
		
		if (adjacentChanged){
			_adjacent = adjacent;
			_blockNeedsUpdate = true;
			SendNewLSA();
		}
	}
	
	private void SendNewLSA()
	{
		_myLsa.neighboursWithMetric = new HashMap<IRouter, Integer>();
		for (RoutedPipe adjacent : _adjacent.keySet()){
			_myLsa.neighboursWithMetric.put(adjacent.getRouter(), _adjacent.get(adjacent).metric);
		}
		_LSDVersion++;
		MainProxy.sendCompressedToAllPlayers((Packet250CustomPayload) new PacketRouterInformation(NetworkConstants.ROUTER_UPDATE_CONTENT, _xCoord , _yCoord, _zCoord, _dimension, this).getPacket());
		//CreateRouteTable();
	}
	
	/**
	 * Create a route table from the link state database
	 */
	private void CreateRouteTable()	{ 
		//Dijkstra!
		
		/** Map of all "approved" routers and the route to get there **/
		HashMap<IRouter, LinkedList<IRouter>> tree =  new HashMap<IRouter, LinkedList<IRouter>>();
		/** The cost to get to an "approved" router **/
		HashMap<IRouter, Integer> treeCost = new HashMap<IRouter, Integer>();
		
		//Init root(er - lol)
		tree.put(this,  new LinkedList<IRouter>());
		treeCost.put(this,  0);
		/** The candidate router and which approved router put it in the candidate list **/
		HashMap<IRouter, IRouter> candidates = new HashMap<IRouter, IRouter>();
		/** The total cost for the candidate route **/
		HashMap<IRouter, Integer> candidatesCost = new HashMap<IRouter, Integer>();
		
		//Init candidates
		for (RoutedPipe pipe :  _adjacent.keySet()){
			candidates.put(SimpleServiceLocator.routerManager.getRouter(pipe.getRouter().getId()), this);
			candidatesCost.put(SimpleServiceLocator.routerManager.getRouter(pipe.getRouter().getId()), _adjacent.get(pipe).metric);
		}

		
		while (!candidates.isEmpty()){
			IRouter lowestCostCandidateRouter = null;
			int lowestCost = Integer.MAX_VALUE;
			for(IRouter candidate : candidatesCost.keySet()){
				if (candidatesCost.get(candidate) < lowestCost){
					lowestCostCandidateRouter = candidate;
					lowestCost = candidatesCost.get(candidate);
				}
			}
			
			IRouter lowestParent = candidates.get(lowestCostCandidateRouter);	//Get the approved parent of the lowest cost candidate
			LinkedList<IRouter> lowestPath = (LinkedList<IRouter>) tree.get(lowestParent).clone();	//Get a copy of the route for the approved router 
			lowestPath.addLast(lowestCostCandidateRouter); //Add to the route to get to the candidate
			
			//Approve the candidate
			tree.put(lowestCostCandidateRouter, lowestPath);
			treeCost.put(lowestCostCandidateRouter, lowestCost);
			
			//Remove from candidate list
			candidates.remove(lowestCostCandidateRouter);
			candidatesCost.remove(lowestCostCandidateRouter);
			
			//Add new candidates from the newly approved route
			for (LSA lsa : SharedLSADatabase){
				if (lsa.source != lowestCostCandidateRouter) continue;				
				for (IRouter newCandidate: lsa.neighboursWithMetric.keySet()){
					if (tree.containsKey(newCandidate)) continue;
					int candidateCost = lowestCost + lsa.neighboursWithMetric.get(newCandidate);
					if (candidates.containsKey(newCandidate) && candidatesCost.get(newCandidate) <= candidateCost){
						continue;
					}
					candidates.put(newCandidate, lowestCostCandidateRouter);
					candidatesCost.put(newCandidate, candidateCost);
				}
			}
		}
		
		
		//Build route table
		_routeTable = new HashMap<IRouter, Orientations>();
		_routeCosts = new HashMap<IRouter, Integer>();
		for (IRouter node : tree.keySet())
		{
			LinkedList<IRouter> route = tree.get(node);
			if (route.size() == 0){
				_routeTable.put(node, Orientations.Unknown);
				continue;
			}
			
			IRouter firstHop = route.getFirst();
			if (firstHop == null) continue;
			CoreRoutedPipe firstPipe = firstHop.getPipe();
			if (firstPipe == null) continue;
			//if (!_adjacent.containsKey(firstHop.getPipe())){
				//System.out.println("FirstHop is not adjacent!");
			//}
			
			if (!_adjacent.containsKey(firstPipe) || _adjacent.get(firstPipe) == null){
				continue;
			}
			
			_routeCosts.put(node, treeCost.get(node));
			_routeTable.put(node, _adjacent.get(firstPipe).exitOrientation);
		}
	}
	
	/*
	@Override
	public LogisticsNetworkTree getNetworkTree(ArrayList<IRouter> excluded, int deep) {
		if(excluded == null) {
			excluded = new ArrayList<IRouter>();
		}
		LinkedHashMap<LogisticsNetworkTree, ExitRoute> list = new LinkedHashMap<LogisticsNetworkTree, ExitRoute>();
		INetworkResistanceFilter filter = null;
		if(getPipe() instanceof INetworkResistanceFilter) {
			filter = (INetworkResistanceFilter) getPipe();
		}
		LogisticsNetworkTree tree = new LogisticsNetworkTree(this, list, filter);
		excluded.add(this);
		
		deep++;
		
		if(deep < 20) { //TODO config Option
			for (RoutedPipe pipe :  _adjacent.keySet()) {
				if(excluded.contains(pipe.getRouter())) continue;
				list.put(pipe.getRouter().getNetworkTree((ArrayList<IRouter>) excluded.clone(), deep), _adjacent.get(pipe));
			}
		}
		
		return tree;
	}
	*/
	
	private LinkedList<Orientations> GetNonRoutedExits()	{
		LinkedList<Orientations> ret = new LinkedList<Orientations>();
		
		outer:
		for (int i = 0 ; i < 6; i++){
			Orientations o = Orientations.values()[i];
			boolean found = false;
			for(ExitRoute route : _adjacent.values()) {
				if (route.exitOrientation == o){
					found = true;
					continue outer; //Its routed
				}
			}
			ret.add(o);	//Its not (might not be a pipe, but its not routed)
		}
		return ret;
	}
	
	@Override
	public void displayRoutes(){
		_laser.displayRoute(this);
	}
	
	@Override
	public void displayRouteTo(IRouter r){
		_laser.displayRoute(this, r);
	}
	
	@Override
	public void inboundItemArrived(RoutedEntityItem routedEntityItem){
		//notify that Item has arrived
		CoreRoutedPipe pipe = getPipe();	
		if (pipe != null && pipe.logic instanceof IRequireReliableTransport){
			((IRequireReliableTransport)pipe.logic).itemArrived(ItemIdentifier.get(routedEntityItem.getItemStack()));
		}
	}

	@Override
	public void itemDropped(RoutedEntityItem routedEntityItem) {
		//TODO
	}
	
	/**
	 * Flags the last sent LSA as expired. Each router will be responsible of purging it from its database.
	 */
	@Override
	public void destroy() {
		if (SharedLSADatabase.contains(_myLsa)){
			SharedLSADatabase.remove(_myLsa);
			_LSDVersion++;
		}
		SimpleServiceLocator.routerManager.removeRouter(this.id);
	}

	@Override
	public void update(boolean doFullRefresh){
		if (doFullRefresh) {
			recheckAdjacent();
			if (_blockNeedsUpdate){
				CoreRoutedPipe pipe = getPipe();
				if (pipe == null) return;
				pipe.worldObj.markBlockAsNeedsUpdate(pipe.xCoord, pipe.yCoord, pipe.zCoord);
				pipe.refreshRender();
				_blockNeedsUpdate = false;
			}
		}
		ensureRouteTableIsUpToDate();
	}

	/************* IROUTER *******************/
	
	
	@Override
	public void sendRoutedItem(ItemStack item, IRouter destination, Position origin) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isRoutedExit(Orientations o){
		return !GetNonRoutedExits().contains(o);
	}

	@Override
	public Orientations getExitFor(UUID id) {
		return this.getRouteTable().get(SimpleServiceLocator.routerManager.getRouter(id));
	}
	
	@Override
	public boolean hasRoute(UUID id) {
		if (!SimpleServiceLocator.routerManager.isRouter(id)) return false;
		
		IRouter r = SimpleServiceLocator.routerManager.getRouter(id);
		
		if (!this.getRouteTable().containsKey(r)) return false;
		
		return true;
	}
	
	@Override
	public ILogisticsModule getLogisticsModule() {
		CoreRoutedPipe pipe = this.getPipe();
		if (pipe == null) return null;
		return pipe.getLogisticsModule();
	}

	@Override
	public List<ILogisticsPowerProvider> getPowerProvider() {
		List<ILogisticsPowerProvider> list = new ArrayList<ILogisticsPowerProvider>();
		//addSubowerProvider(tree, list);
		for(IRouter router:_routeTable.keySet()) {
			if(router instanceof ServerRouter) {
				for(ILogisticsPowerProvider provider:((ServerRouter)router).getConnectedPowerProvider()) {
					if(list.contains(provider)) continue;
					list.add(provider);
				}
			}
		}
		return list;
	}
	
	/*
	private void addSubowerProvider(LogisticsNetworkTree node, List<ILogisticsPowerProvider> list) {
		if(node.router instanceof ServerRouter) {
			for(ILogisticsPowerProvider provider:((ServerRouter)node.router).getConnectedPowerProvider()) {
				if(list.contains(provider)) continue;
				list.add(provider);
			}
		}
		for(LogisticsNetworkTree subNode:node.connection.keySet()) {
			if(node.connection.get(subNode).isPipeLess) continue;
			addSubowerProvider(subNode, list);
		}
	}
	*/
	
	@Override
	public List<ILogisticsPowerProvider> getConnectedPowerProvider() {
		CoreRoutedPipe pipe = getPipe();
		if(pipe instanceof PipeItemsBasicLogistics) {
			return ((PipeItemsBasicLogistics)pipe).getConnectedPowerProviders();
		} else {
			return new ArrayList<ILogisticsPowerProvider>();
		}
	}
}


