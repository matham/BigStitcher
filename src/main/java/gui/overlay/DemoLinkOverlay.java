package gui.overlay;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;

public class DemoLinkOverlay implements OverlayRenderer, TransformListener< AffineTransform3D >,SelectedViewDescriptionListener< AbstractSpimData<?> >
{

	private StitchingResults stitchingResults;
	private AbstractSpimData< ? > spimData;	
	private AffineTransform3D viewerTransform;	
	public boolean isActive;
	private ArrayList<Pair<Set<ViewId>, Set<ViewId>>> activeLinks;
	private double rThresh = .4;

	
	public DemoLinkOverlay( StitchingResults res, AbstractSpimData< ? > spimData)
	{
		this.stitchingResults = res;
		this.spimData = spimData;
		viewerTransform = new AffineTransform3D();
		isActive = false;
		activeLinks = new ArrayList<>();
	}
	
	
	@Override
	public void transformChanged(AffineTransform3D transform)
	{
		this.viewerTransform = transform;
		
	}

	@Override
	public void drawOverlays(Graphics g)
	{
		// dont do anything if the overlay was set to inactive or we have no Tile selected (no links to display)
		if (!isActive || activeLinks.size() == 0)
			return;
		
		for ( Pair<Set<ViewId>, Set<ViewId>> p: activeLinks)
		{
		
		// local coordinates of views, without BDV transform 
					final double[] lPos1 = new double[ 3 ];
					final double[] lPos2 = new double[ 3 ];
					// global coordianates, after BDV transform
					final double[] gPos1 = new double[ 3 ];
					final double[] gPos2 = new double[ 3 ];
					
					BasicViewDescription<?> vdA = spimData.getSequenceDescription().getViewDescriptions().get( p.getA().iterator().next() );
					BasicViewDescription<?> vdB = spimData.getSequenceDescription().getViewDescriptions().get( p.getB().iterator().next() );
					ViewRegistration vrA = spimData.getViewRegistrations().getViewRegistration(  p.getA().iterator().next() );
					ViewRegistration vrB = spimData.getViewRegistrations().getViewRegistration(  p.getB().iterator().next() );

					long[] sizeA = new long[vdA.getViewSetup().getSize().numDimensions()];
					long[] sizeB = new long[vdB.getViewSetup().getSize().numDimensions()];
					spimData.getSequenceDescription().getViewDescriptions().get( p.getA().iterator().next() ).getViewSetup().getSize().dimensions( sizeA );
					spimData.getSequenceDescription().getViewDescriptions().get( p.getB().iterator().next() ).getViewSetup().getSize().dimensions( sizeB );
					
					// TODO: this uses the transform of the first view in the set, maybe do something better?
					AffineTransform3D vt1 = spimData.getViewRegistrations().getViewRegistration( p.getA().iterator().next() ).getModel();
					AffineTransform3D vt2 = spimData.getViewRegistrations().getViewRegistration( p.getB().iterator().next() ).getModel();
					
					boolean overlaps = SimpleBoundingBoxOverlap.overlaps( SimpleBoundingBoxOverlap.getBoundingBox(	vdA.getViewSetup(), vrA ), SimpleBoundingBoxOverlap.getBoundingBox( vdB.getViewSetup(), vrB ) );
					
					if (!overlaps)
						continue;
					
					final AffineTransform3D transform = new AffineTransform3D();
					transform.preConcatenate( viewerTransform );

					for(int i = 0; i < 3; i++)
					{
						// start from middle of view
						lPos1[i] += sizeA[i] / 2;
						lPos2[i] += sizeB[i] / 2;
					}

					vt1.apply( lPos1, lPos1 );
					vt2.apply( lPos2, lPos2 );
					
					transform.apply( lPos1, gPos1 );
					transform.apply( lPos2, gPos2 );
					
					if (stitchingResults.getPairwiseResults().containsKey( p ) && stitchingResults.getPairwiseResultsForPair( p ).r() > rThresh)
					{
						g.setColor( Color.GREEN );
						
					}
					else
						continue;
						//g.setColor( Color.GRAY );
					
					g.drawLine((int) gPos1[0],(int) gPos1[1],(int) gPos2[0],(int) gPos2[1] );
		}
		
		
	}
	
	public void clearActiveLinks()
	{
		activeLinks.clear();
	}
	
	public void setActiveLinks(List<Pair<Set<ViewId>, Set<ViewId>>> vids)
	{
		activeLinks.clear();
		activeLinks.addAll( vids );
	}

	@Override
	public void setCanvasSize(int width, int height){}


	@Override
	public void selectedViewDescriptions(
			List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions)
	{
		List<Pair<Set<ViewId>, Set<ViewId>>> res = new ArrayList<>();
		for (int i = 0; i<viewDescriptions.size(); i++)
			for (int j = i+1; j<viewDescriptions.size(); j++)
			{
				Set<ViewId> setA = new HashSet<>();
				setA.addAll( viewDescriptions.get( i ) );
				Set<ViewId> setB = new HashSet<>();
				setB.addAll( viewDescriptions.get( j ) );
				res.add( new ValuePair< Set<ViewId>, Set<ViewId> >( setA, setB ) );
			}
		setActiveLinks( res );
		
	}


	@Override
	public void updateContent(AbstractSpimData< ? > data)
	{
	}


	@Override
	public void save()
	{
	}


	@Override
	public void quit()
	{
	}

}