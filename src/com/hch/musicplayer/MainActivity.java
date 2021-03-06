package com.hch.musicplayer;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static String tag = "MainActivityLog";
	private ListView musicListView;
	private ImageButton prevButton, playButton, nextButton, orderButton,
			cycleButton;
	private TextView music_title, music_artist, music_duration;
	private SeekBar music_seekbar;
	private List<Map<String, Object>> musicList = new ArrayList<Map<String, Object>>();
	private SimpleAdapter adapter;
	private MediaPlayer mediaPlayer;
	private int musicIndex = -1;
	private Timer timer;
	private TimerTask timerTask;
	private boolean isSeekBarChanging = false;// 互斥变量，防止定时器与SeekBar拖动时进度冲突
	private String musicDuration;
	private SharedPreferences sharedPreferences;
	private SharedPreferences.Editor sharedPreferences_Editor;
	private int duration;// 歌曲时长
	private int currentposition;// 播放歌曲当前时间
	private boolean isRandom = false;// 是否随机播放
	private boolean isListCycle = true;// 是否列表循环

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			super.handleMessage(message);
			int what = message.what;
			if (what == 1) {
				String durationShow = message.getData().getString(
						"durationShow");
				music_duration.setText(durationShow);
			} else if (what == 2) {
				adapter.notifyDataSetChanged();
				playButton.setBackgroundResource(R.drawable.pausebutton);
			}

		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		musicListView = (ListView) findViewById(R.id.musicListView);
		prevButton = (ImageButton) findViewById(R.id.prevButton);
		playButton = (ImageButton) findViewById(R.id.playButton);
		nextButton = (ImageButton) findViewById(R.id.nextButton);
		orderButton = (ImageButton) findViewById(R.id.orderButton);
		cycleButton = (ImageButton) findViewById(R.id.cycleButton);
		music_title = (TextView) findViewById(R.id.music_title);
		music_artist = (TextView) findViewById(R.id.music_artist);
		music_duration = (TextView) findViewById(R.id.music_duration);
		music_seekbar = (SeekBar) findViewById(R.id.music_seekbar);

		sharedPreferences = getSharedPreferences("playmusic", MODE_PRIVATE);
		sharedPreferences_Editor = sharedPreferences.edit();
		musicIndex = sharedPreferences.getInt("musicIndex", -1);
		duration = sharedPreferences.getInt("duration", 0);
		currentposition = sharedPreferences.getInt("currentposition", 0);
		isRandom = sharedPreferences.getBoolean("isRandom", false);
		isListCycle = sharedPreferences.getBoolean("isListCycle", true);

		if (isRandom) {
			orderButton.setBackgroundResource(R.drawable.randombutton);
		} else {
			orderButton.setBackgroundResource(R.drawable.unrandombutton);
		}

		if (isListCycle) {
			cycleButton.setBackgroundResource(R.drawable.circlebutton);
		} else {
			cycleButton.setBackgroundResource(R.drawable.singlebutton);
		}

		Cursor cursor = getContentResolver().query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
				MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
		// 遍历媒体数据库
		if (cursor.moveToFirst()) {
			while (!cursor.isAfterLast()) {
				// 歌曲编号
				int id = cursor.getInt(cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
				// 歌曲标题
				String tilte = cursor.getString(cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
				// 歌曲的专辑名：MediaStore.Audio.Media.ALBUM
				String album = cursor.getString(cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
				int albumId = cursor.getInt(cursor
						.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
				// 歌曲的歌手名： MediaStore.Audio.Media.ARTIST
				String artist = cursor.getString(cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
				// 歌曲文件的路径 ：MediaStore.Audio.Media.DATA
				String url = cursor.getString(cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
				// 歌曲的总播放时长 ：MediaStore.Audio.Media.DURATION
				int duration = cursor
						.getInt(cursor
								.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
				String durationShow = String.format(
						"%02d:%02d",
						TimeUnit.MILLISECONDS.toMinutes(duration),
						TimeUnit.MILLISECONDS.toSeconds(duration)
								- TimeUnit.MINUTES
										.toSeconds(TimeUnit.MILLISECONDS
												.toMinutes(duration)));
				// 歌曲文件的大小 ：MediaStore.Audio.Media.SIZE
				long size = cursor.getLong(cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));
				String sizeStr = new DecimalFormat("###,###,###.##")
						.format((float) (size / 1024.0 / 1024.0)) + " MB";

				// 大于800K
				if (size > 1024 * 800) {
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("id", id);
					map.put("title", tilte);
					map.put("album", album);
					map.put("artist", artist);
					map.put("url", url);
					map.put("durationShow", durationShow);
					map.put("sizeStr", sizeStr);

					map.put("bitmap", R.drawable.music);
					map.put("playingImg", null);
					musicList.add(map);
				}
				cursor.moveToNext();
			}
		}

		adapter = new SimpleAdapter(MainActivity.this, musicList,
				R.layout.musiclist_item, new String[] { "bitmap", "playingImg",
						"title", "artist", "sizeStr", "durationShow" },
				new int[] { R.id.musiclist_image, R.id.musiclist_playing,
						R.id.musiclist_title, R.id.musiclist_artist,
						R.id.musiclist_size, R.id.musiclist_duration });
		musicListView.setAdapter(adapter);

		if (mediaPlayer == null) {
			mediaPlayer = new MediaPlayer();
		}
		if (timer == null) {
			timer = new Timer();
		}

		if (musicIndex > -1) {
			String title = (String) musicList.get(musicIndex).get("title");
			String artist = (String) musicList.get(musicIndex).get("artist");
			String url = (String) musicList.get(musicIndex).get("url");
			try {
				mediaPlayer.setDataSource(url);
				mediaPlayer.prepare();
				mediaPlayer.seekTo(currentposition);
			} catch (IOException e) {
				Log.e(tag, Log.getStackTraceString(e));
			}

			music_title.setText(title);
			music_artist.setText(artist);
			music_seekbar.setMax(duration);// 设置进度条
			music_seekbar.setProgress(currentposition);
			musicList.get(musicIndex).put("playingImg", R.drawable.playing1);
			adapter.notifyDataSetChanged();
			musicListView.smoothScrollToPosition(musicIndex);

			String currentDuration = String.format(
					"%02d:%02d",
					TimeUnit.MILLISECONDS.toMinutes(currentposition),
					TimeUnit.MILLISECONDS.toSeconds(currentposition)
							- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
									.toMinutes(currentposition)));
			musicDuration = String.format(
					"%02d:%02d",
					TimeUnit.MILLISECONDS.toMinutes(mediaPlayer.getDuration()),
					TimeUnit.MILLISECONDS.toSeconds(mediaPlayer.getDuration())
							- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
									.toMinutes(mediaPlayer.getDuration())));
			String durationShow = currentDuration + "/" + musicDuration;
			music_duration.setText(durationShow);
		}

		musicListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				playMusic(position, 0);
			}
		});

		music_seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				mediaPlayer.seekTo(seekBar.getProgress());
				isSeekBarChanging = false;
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				isSeekBarChanging = true;
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (progress == music_seekbar.getMax() && !isSeekBarChanging) {
					int position = musicIndex + 1;
					// 列表循环
					if (isListCycle) {
						// 随机播放
						if (isRandom) {
							position = getRandomIndex();
						} else if (position > musicList.size() - 1) {
							position = 0;
						}
					} else {
						position = musicIndex;
					}

					playMusic(position, 0);
				}
			}
		});

		playButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (musicIndex == -1) {
					return;
				}
				if (mediaPlayer.isPlaying()) {
					playButton.setBackgroundResource(R.drawable.playbutton);
					mediaPlayer.pause();
					timerTask.cancel();
				} else {
					playButton.setBackgroundResource(R.drawable.pausebutton);
					mediaPlayer.start();
					timerTask = new TimerTask() {
						@Override
						public void run() {
							if (isSeekBarChanging) {
								return;
							}
							music_seekbar.setProgress(mediaPlayer
									.getCurrentPosition());

							String currentDuration = String.format(
									"%02d:%02d",
									TimeUnit.MILLISECONDS.toMinutes(mediaPlayer
											.getCurrentPosition()),
									TimeUnit.MILLISECONDS.toSeconds(mediaPlayer
											.getCurrentPosition())
											- TimeUnit.MINUTES
													.toSeconds(TimeUnit.MILLISECONDS
															.toMinutes(mediaPlayer
																	.getCurrentPosition())));
							String durationShow = currentDuration + "/"
									+ musicDuration;
							Message message = new Message();
							message.what = 1;
							message.getData().putString("durationShow",
									durationShow);
							handler.sendMessage(message);
						}
					};
					timer.schedule(timerTask, 0, 10);
				}

			}
		});

		prevButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (musicIndex == -1) {
					return;
				}
				int position = musicIndex - 1;
				if (position < 0) {
					return;
				}
				playMusic(position, 0);

			}
		});

		nextButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (musicIndex == -1) {
					return;
				}
				int position = musicIndex + 1;
				if (position > musicList.size() - 1) {
					return;
				}
				playMusic(position, 0);
			}
		});

		orderButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				isRandom = isRandom == true ? false : true;
				if (isRandom) {
					orderButton.setBackgroundResource(R.drawable.randombutton);
					Toast.makeText(MainActivity.this, "切换为随机播放",
							Toast.LENGTH_SHORT).show();
				} else {
					orderButton
							.setBackgroundResource(R.drawable.unrandombutton);
					Toast.makeText(MainActivity.this, "切换为顺序播放",
							Toast.LENGTH_SHORT).show();
				}

			}
		});

		cycleButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				isListCycle = isListCycle == true ? false : true;
				if (isListCycle) {
					cycleButton.setBackgroundResource(R.drawable.circlebutton);
					Toast.makeText(MainActivity.this, "切换为列表循环",
							Toast.LENGTH_SHORT).show();
				} else {
					cycleButton.setBackgroundResource(R.drawable.singlebutton);
					Toast.makeText(MainActivity.this, "切换为单曲循环",
							Toast.LENGTH_SHORT).show();
				}
			}
		});

	}

	private void playMusic(int position, int duration) {
		String title = (String) musicList.get(position).get("title");
		String artist = (String) musicList.get(position).get("artist");
		String url = (String) musicList.get(position).get("url");

		if (musicIndex != -1) {
			musicList.get(musicIndex).put("playingImg", null);
		}
		musicList.get(position).put("playingImg", R.drawable.playing1);
		Message message = new Message();
		message.what = 2;
		handler.sendMessage(message);
		musicListView.smoothScrollToPosition(position);

		if (timerTask != null) {
			timerTask.cancel();
		}
		try {
			mediaPlayer.reset();
			mediaPlayer.setDataSource(url);
			mediaPlayer.prepare();
			mediaPlayer.seekTo(duration);
			mediaPlayer.start();

			musicDuration = String.format(
					"%02d:%02d",
					TimeUnit.MILLISECONDS.toMinutes(mediaPlayer.getDuration()),
					TimeUnit.MILLISECONDS.toSeconds(mediaPlayer.getDuration())
							- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
									.toMinutes(mediaPlayer.getDuration())));

			music_title.setText(title);
			music_artist.setText(artist);
			music_seekbar.setMax(mediaPlayer.getDuration());// 设置进度条

			timerTask = new TimerTask() {
				@Override
				public void run() {
					if (isSeekBarChanging) {
						return;
					}
					music_seekbar.setProgress(mediaPlayer.getCurrentPosition());

					String currentDuration = String
							.format("%02d:%02d",
									TimeUnit.MILLISECONDS.toMinutes(mediaPlayer
											.getCurrentPosition()),
									TimeUnit.MILLISECONDS.toSeconds(mediaPlayer
											.getCurrentPosition())
											- TimeUnit.MINUTES
													.toSeconds(TimeUnit.MILLISECONDS
															.toMinutes(mediaPlayer
																	.getCurrentPosition())));
					String durationShow = currentDuration + "/" + musicDuration;
					Message message = new Message();
					message.what = 1;
					message.getData().putString("durationShow", durationShow);
					handler.sendMessage(message);
				}
			};
			timer.schedule(timerTask, 0, 10);

		} catch (IOException e) {
			Log.e(tag, Log.getStackTraceString(e));
		}
		musicIndex = position;
	}

	private int getRandomIndex() {
		int r = (int) (Math.random() * musicList.size());
		if (r == musicIndex) {
			getRandomIndex();
		}

		return r;
	}

	// @Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			AlertDialog.Builder builder = new Builder(MainActivity.this);
			builder.setMessage("是否退出音乐播放器？");
			builder.setTitle("提示");
			builder.setPositiveButton("取消",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
			builder.setNegativeButton("退出",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							sharedPreferences_Editor.putInt("musicIndex",
									musicIndex);
							sharedPreferences_Editor.putInt("duration",
									mediaPlayer.getDuration());
							sharedPreferences_Editor.putInt("currentposition",
									mediaPlayer.getCurrentPosition());
							sharedPreferences_Editor.putBoolean("isRandom",
									isRandom);
							sharedPreferences_Editor.putBoolean("isListCycle",
									isListCycle);
							sharedPreferences_Editor.commit();

							dialog.dismiss();
							System.exit(0);
						}
					});
			builder.create().show();
		} else {
			return super.onKeyDown(keyCode, event);
		}
		return true;
	}
}
