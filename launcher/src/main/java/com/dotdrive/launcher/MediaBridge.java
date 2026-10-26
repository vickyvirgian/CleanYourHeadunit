package com.dotdrive.launcher;

import android.content.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.Handler;
import java.util.*;

final class MediaBridge {
    private final Context context;
    private final Handler handler;
    private final Runnable changed;
    private final MediaSessionManager manager;
    private MediaController current;
    private MediaMetadata metadata;
    private PlaybackState state;
    private boolean watching, allowed;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { metadata=m;changed.run(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { state=s;changed.run(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::choose;
    MediaBridge(Context c, Handler h, Runnable changed) {
        context=c;handler=h;this.changed=changed;manager=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }
    void start() {
        stop();
        try {
            ComponentName access=new ComponentName(context,MediaAccessService.class);
            manager.addOnActiveSessionsChangedListener(sessions,access,handler);watching=true;allowed=true;
            choose(manager.getActiveSessions(access));
        } catch(RuntimeException e) {allowed=false;choose(Collections.emptyList());}
    }
    void stop() {
        try {if(watching)manager.removeOnActiveSessionsChangedListener(sessions);}catch(RuntimeException ignored){}
        watching=false;if(current!=null)try{current.unregisterCallback(callback);}catch(RuntimeException ignored){}current=null;metadata=null;state=null;
    }
    private void refresh() {
        try {choose(manager.getActiveSessions(new ComponentName(context,MediaAccessService.class)));}
        catch(SecurityException e){allowed=false;choose(Collections.emptyList());}
    }
    private void choose(List<MediaController> list) {
        if(current!=null)try{current.unregisterCallback(callback);}catch(RuntimeException ignored){}
        current=null;metadata=null;state=null;
        if(list!=null) for(MediaController c:list) {
            if(current==null)current=c;
            PlaybackState s;try{s=c.getPlaybackState();}catch(RuntimeException e){continue;}
            if(s!=null && s.getState()==PlaybackState.STATE_PLAYING){current=c;break;}
        }
        if(current!=null)try{current.registerCallback(callback,handler);metadata=current.getMetadata();state=current.getPlaybackState();}catch(RuntimeException e){current=null;metadata=null;state=null;}
        changed.run();
    }
    boolean allowed(){return allowed;}
    MediaController current(){return current;}
    MediaMetadata metadata(){return metadata;}
    PlaybackState state(){return state;}
    void control(int which) {
        if(current==null)return;
        PlaybackState state=current.getPlaybackState();if(state==null)return;
        long action=which<0?PlaybackState.ACTION_SKIP_TO_PREVIOUS:which>0?PlaybackState.ACTION_SKIP_TO_NEXT:
            state.getState()==PlaybackState.STATE_PLAYING?PlaybackState.ACTION_PAUSE:PlaybackState.ACTION_PLAY;
        if((state.getActions()&action)==0 && (which!=0 || (state.getActions()&PlaybackState.ACTION_PLAY_PAUSE)==0))return;
        MediaController.TransportControls c=current.getTransportControls();
        if(which<0)c.skipToPrevious();else if(which>0)c.skipToNext();else if(state.getState()==PlaybackState.STATE_PLAYING)c.pause();else c.play();
    }
}
