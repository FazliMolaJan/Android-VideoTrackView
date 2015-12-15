/*
 * Android-VideoTrackView
 * https://github.com/crust87/Android-VideoTrackView
 *
 * Mabi
 * crust87@gmail.com
 * last modify 2015-12-14
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crust87.videotrackview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.ArrayList;


public class VideoTrackView extends SurfaceView implements SurfaceHolder.Callback {
	// Components for SurfaceView
	private SurfaceHolder mHolder;

	// Components
    private Context mContext;
	private ArrayList<Bitmap> mThumbnailList;
	private Paint mBackgroundPaint;
	private Rect mBackgroundRect;
	protected Track mTrack;

	private OnTouchListener mOnTouchListener;

	// Attributes
    protected float mScreenDuration = 10000f;
	protected int mThumbnailPerScreen = 6;
	protected float mThumbnailDuration = mScreenDuration / mThumbnailPerScreen;
	protected int mTrackPadding;

	protected int mWidth;						// view width
	protected int mHeight;					// view height
	protected int mVideoDuration;				// video duration
	protected int mVideoDurationWidth;		// video duration in pixel
	protected float millisecondsPerWidth;			// milliseconds per width
	protected int minWidth;					// minimum duration in pixel
	protected int thumbWidth;					// one second of width in pixel

	// Working Variable;
	protected int currentPosition;			// current start position
	protected float pastX;					// past position x of touch event

	// event listener
	private OnUpdatePositionListener mOnUpdatePositionListener;

	public VideoTrackView(Context context) {
		super(context);
        mContext = context;

        initAttributes();
        initTrackView();
	}

    public VideoTrackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        initAttributes(context, attrs, 0);
        initTrackView();
    }

    public VideoTrackView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;

        initAttributes(context, attrs, defStyleAttr);
        initTrackView();
    }

    private void initAttributes() {
        mScreenDuration = 10000f;
        mThumbnailPerScreen = 6;
        mThumbnailDuration = mScreenDuration / mThumbnailPerScreen;
		mTrackPadding = mContext.getResources().getDimensionPixelOffset(R.dimen.default_track_padding);
    }

    private void initAttributes(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.VideoTrackView, defStyleAttr, 0);

        mScreenDuration = typedArray.getFloat(R.styleable.VideoTrackView_screen_duration, 10000f);
        mThumbnailPerScreen = typedArray.getInteger(R.styleable.VideoTrackView_thumbnail_per_screen, 6);
        mThumbnailDuration = mScreenDuration / mThumbnailPerScreen;

		int lDefaultTrackPadding = mContext.getResources().getDimensionPixelOffset(R.dimen.default_track_padding);
		mTrackPadding = typedArray.getDimensionPixelOffset(R.styleable.VideoTrackView_track_padding, lDefaultTrackPadding);
    }

    protected void initTrackView() {
        mHolder = getHolder();
        mHolder.addCallback(this);

        mThumbnailList = new ArrayList<Bitmap>();
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(Color.parseColor("#222222"));
        setWillNotDraw(false);
    }

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(mOnTouchListener != null) {
			if(mOnTouchListener.onTouch(this, event)) {
				return true;
			}
		}

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				if(mOnUpdatePositionListener != null) {
					mOnUpdatePositionListener.onUpdatePositionStart();
				}
				pastX = event.getX();
			case MotionEvent.ACTION_MOVE:
				updateTrackPosition((int) (event.getX() - pastX));
				pastX = event.getX();
				break;
			case MotionEvent.ACTION_UP:
				if(mOnUpdatePositionListener != null) {
					mOnUpdatePositionListener.onUpdatePositionEnd(currentPosition);
				}
                break;
		}

		return true;
	}

	@Override
	public void setOnTouchListener(OnTouchListener listener) {
		mOnTouchListener = listener;
	}

	public boolean setVideo(String path) {
		mThumbnailList.clear();
		MediaMetadataRetriever retriever = new  MediaMetadataRetriever();
		retriever.setDataSource(path);

		String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

		try {
			mVideoDuration = Integer.parseInt(duration);
		} catch(NumberFormatException e) {
			mVideoDuration = 0;
		}

		if(mVideoDuration == 0) {
			return false;
		}

		mVideoDurationWidth = (int) (mVideoDuration * millisecondsPerWidth);
		currentPosition = 0;

		mTrack.right = mVideoDurationWidth;

		// create thumbnail for draw track
        int thumbDurationInMicro = (int) (mThumbnailDuration * 1000);
		for(int i = 0; i < mVideoDuration * 1000; i += thumbDurationInMicro) {
			Bitmap thumbnail = retriever.getFrameAtTime(i);
			if(thumbnail != null) {
				mThumbnailList.add(scaleCenterCrop(thumbnail, thumbWidth, mHeight - (mTrackPadding * 2)));
				thumbnail.recycle();
			}
		}

		retriever.release();
        invalidate();
		return true;
	}

	// update track position
	// int x: it's actually delta x
	private void updateTrackPosition(float x) {
		// check next position in boundary
		if(mTrack.left + x > 0) {
			x = -mTrack.left;
		}

		if(mTrack.right + x < minWidth) {
			x = minWidth - mTrack.right;
		}

		mTrack.left += x;
		mTrack.right += x;

		currentPosition = (int) -(mTrack.left / millisecondsPerWidth);
		if(x < 0) {
			int nextDuration = mVideoDuration - currentPosition;
		}

		if(mOnUpdatePositionListener != null) {
			mOnUpdatePositionListener.onUpdatePosition(currentPosition);
		}

		invalidate();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {

	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		mWidth = width;
		mHeight = height;

        millisecondsPerWidth = mWidth / mScreenDuration;
        mVideoDurationWidth = (int) (mVideoDuration * millisecondsPerWidth);
		minWidth = 0;
        thumbWidth = (int) (millisecondsPerWidth * mThumbnailDuration);

		mBackgroundRect = new Rect(0, 0, mWidth, mHeight);
        mTrack = new Track(0, mTrackPadding, mVideoDurationWidth, mHeight - mTrackPadding);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		canvas.drawRect(mBackgroundRect, mBackgroundPaint);
		mTrack.draw(canvas);
	}

	private Bitmap scaleCenterCrop(Bitmap source, int newWidth, int newHeight) {
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();

		float xScale = (float) newWidth / sourceWidth;
		float yScale = (float) newHeight / sourceHeight;
		float scale = Math.max(xScale, yScale);

		float scaledWidth = scale * sourceWidth;
		float scaledHeight = scale * sourceHeight;

		float left = (newWidth - scaledWidth) / 2;
		float top = (newHeight - scaledHeight) / 2;

		RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);

		Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
		Canvas canvas = new Canvas(dest);
		canvas.drawBitmap(source, null, targetRect, null);

		return dest;
	}

	public void setOnUpdatePositionListener(OnUpdatePositionListener pOnUpdatePositionListener) {
		mOnUpdatePositionListener = pOnUpdatePositionListener;
	}

    public void setScreenDuration(float screenDuration) {
        mScreenDuration = screenDuration;
        mThumbnailDuration = mScreenDuration / mThumbnailPerScreen;

        invalidate();
    }

    public void setThumbnailPerScreen(int thumbnailPerScreen) {
        mThumbnailPerScreen = thumbnailPerScreen;
        mThumbnailDuration = mScreenDuration / mThumbnailPerScreen;

        invalidate();
    }

	// Track class
	protected class Track {
		private Paint mTrackPaint;
		private Paint mThumbnailPaint;
		private Paint mBitmapPaint;

		public int left;
		public int top;
		public int right;
		public int bottom;

		private int lineBoundLeft;
		private int lineBoundRight;

		private Bitmap mBitmap;
		private Canvas mCanvas;

		private Rect mBound;

		private Track(int left, int top, int right, int bottom) {
			mTrackPaint = new Paint();
			mTrackPaint.setColor(Color.parseColor("#000000"));
			mTrackPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
			mThumbnailPaint = new Paint();

			mBitmapPaint = new Paint();

			lineBoundLeft = -thumbWidth;
			lineBoundRight = mWidth;

			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;

			mBound = new Rect(0, top, mWidth, bottom);

			mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
			mCanvas = new Canvas(mBitmap);
		}

		private void draw(Canvas canvas) {
			mCanvas.drawRect(0, 0, mWidth, mHeight, mBackgroundPaint);
			mCanvas.drawRect(left, top, right, bottom, mTrackPaint);
            drawThumbnail();
			mCanvas.drawRect(right, 0, mWidth, mHeight, mBackgroundPaint);
			canvas.drawBitmap(mBitmap, null, mBound, mBitmapPaint);
		}

		private void drawThumbnail() {
            int i = 0;
			while(i < mThumbnailList.size()) {
				int x = (left + i * thumbWidth);

				if(x < lineBoundLeft) {
					int next = x / -thumbWidth < 1 ? 1 : x / -thumbWidth;
					i += next;
					continue;
				}

				if(x > lineBoundRight) {
					break;
				}
				// draw thumbnail it's if visible
				mCanvas.drawBitmap(mThumbnailList.get(i), x, top, mThumbnailPaint);
				i++;
			}
		}
	}

	// video time position change listener
	public interface OnUpdatePositionListener {
		void onUpdatePositionStart();
		void onUpdatePosition(int seek);
		void onUpdatePositionEnd(int seek);
	}
}
